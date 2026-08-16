package com.gymlet.config;

import com.gymlet.domain.AppUser;
import com.gymlet.domain.BodyWeightLog;
import com.gymlet.domain.Exercise;
import com.gymlet.domain.ExerciseNote;
import com.gymlet.domain.SetLog;
import com.gymlet.domain.WorkoutDay;
import com.gymlet.domain.WorkoutSession;
import com.gymlet.repository.AppUserRepository;
import com.gymlet.repository.BodyWeightLogRepository;
import com.gymlet.repository.ExerciseNoteRepository;
import com.gymlet.repository.SetLogRepository;
import com.gymlet.repository.WorkoutDayRepository;
import com.gymlet.repository.WorkoutSessionRepository;
import com.gymlet.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-time, idempotent migration of the pre-multi-user database:
 *
 *  1. workout_day.day_number used to be globally unique; per-user plans collide,
 *     so the old unique constraint/index on that column alone is dropped and a
 *     composite UNIQUE (user_id, day_number) is ensured instead. This is done
 *     with DDL (never relying on Hibernate's ddl-auto), and it must work on
 *     PostgreSQL where the legacy constraint is backed by an index that cannot
 *     simply be DROPped ("...constraint ... requires it").
 *  2. The legacy single-user world (an AppUser with no PIN yet, plus any
 *     workouts/history/bodyweight owned by nobody) is claimed: the legacy user
 *     gets a username, gets an independent copy of the default split, and all
 *     existing history is re-pointed to that copy.
 *
 * Existing data is never deleted. Both steps are safe to re-run: they detect
 * work already done and skip it.
 */
@Component
@Order(2)
public class SchemaMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigration.class);

    private final DataSource dataSource;
    private final AppUserRepository userRepository;
    private final WorkoutDayRepository workoutDayRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final SetLogRepository setLogRepository;
    private final ExerciseNoteRepository exerciseNoteRepository;
    private final BodyWeightLogRepository bodyWeightRepository;
    private final AuthService authService;

    public SchemaMigration(DataSource dataSource,
                           AppUserRepository userRepository,
                           WorkoutDayRepository workoutDayRepository,
                           WorkoutSessionRepository sessionRepository,
                           SetLogRepository setLogRepository,
                           ExerciseNoteRepository exerciseNoteRepository,
                           BodyWeightLogRepository bodyWeightRepository,
                           AuthService authService) {
        this.dataSource = dataSource;
        this.userRepository = userRepository;
        this.workoutDayRepository = workoutDayRepository;
        this.sessionRepository = sessionRepository;
        this.setLogRepository = setLogRepository;
        this.exerciseNoteRepository = exerciseNoteRepository;
        this.bodyWeightRepository = bodyWeightRepository;
        this.authService = authService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureDayNumberConstraint();
        migrateLegacyData();
    }

    // ------------------------------------------------------------ constraint

    /**
     * Multi-user requires UNIQUE (user_id, day_number): every user may have
     * their own Day 1..5. The old schema made day_number globally unique.
     * Drops any leftover single-column unique constraint/index and ensures the
     * composite one exists. Runs on every boot and is a no-op once fixed.
     */
    private void ensureDayNumberConstraint() {
        try (Connection c = dataSource.getConnection()) {
            String product = c.getMetaData().getDatabaseProductName().toLowerCase();
            if (product.contains("postgres")) {
                ensureConstraintPostgres(c);
            } else if (product.contains("mysql") || product.contains("mariadb")) {
                ensureConstraintMysql(c);
            } else {
                ensureConstraintH2(c);
            }
        } catch (Exception e) {
            // Never hide a schema problem: if the constraint can't be fixed and
            // we continue, the old global UNIQUE(day_number) would silently
            // break new users. Fail startup loudly instead.
            log.error("Could not fix workout_day.day_number uniqueness: {}", e.getMessage(), e);
            throw new IllegalStateException("workout_day.day_number uniqueness fix failed", e);
        }
    }

    /**
     * PostgreSQL: unique constraints are owned by a table constraint, so the
     * constraint itself must be dropped (DROP INDEX fails with
     * "...constraint ... on table workout_day requires it").
     */
    private void ensureConstraintPostgres(Connection c) throws SQLException {
        boolean compositeExists = false;
        List<String> toDrop = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT con.conname AS name,
                            string_agg(att.attname, ',' ORDER BY k.ord) AS cols
                     FROM pg_constraint con
                     JOIN pg_class rel ON rel.oid = con.conrelid
                     JOIN pg_namespace ns ON ns.oid = rel.relnamespace
                     CROSS JOIN LATERAL unnest(con.conkey) WITH ORDINALITY AS k(attnum, ord)
                     JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = k.attnum
                     WHERE rel.relname = 'workout_day'
                       AND ns.nspname = current_schema()
                       AND con.contype = 'u'
                     GROUP BY con.conname
                     """)) {
            while (rs.next()) {
                String name = rs.getString("name");
                String cols = rs.getString("cols");
                if ("day_number".equals(cols)) {
                    toDrop.add(name);
                } else if (isUserDay(cols)) {
                    compositeExists = true;
                }
            }
        }
        for (String name : toDrop) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("ALTER TABLE workout_day DROP CONSTRAINT \"" + name + "\"");
                log.info("Dropped legacy unique constraint '{}' on workout_day.day_number", name);
            }
        }
        // Any leftover unique index (not constraint-backed) on day_number alone.
        List<String> orphanIndexes = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT ic.relname AS name
                     FROM pg_index i
                     JOIN pg_class tc ON tc.oid = i.indrelid
                     JOIN pg_class ic ON ic.oid = i.indexrelid
                     JOIN pg_namespace ns ON ns.oid = tc.relnamespace
                     LEFT JOIN pg_constraint con ON con.conindid = i.indexrelid
                     WHERE tc.relname = 'workout_day'
                       AND ns.nspname = current_schema()
                       AND i.indisunique
                       AND con.conindid IS NULL
                       AND i.indnkeyatts = 1
                       AND i.indkey[0] = (SELECT attnum FROM pg_attribute
                                          WHERE attrelid = i.indrelid AND attname = 'day_number')
                     """)) {
            while (rs.next()) {
                orphanIndexes.add(rs.getString("name"));
            }
        }
        for (String name : orphanIndexes) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DROP INDEX IF EXISTS \"" + name + "\"");
                log.info("Dropped legacy unique index '{}' on workout_day.day_number", name);
            }
        }
        if (!compositeExists) {
            createComposite(c);
        }
    }

    /** MySQL/MariaDB: a unique constraint IS its index, so DROP INDEX removes both. */
    private void ensureConstraintMysql(Connection c) throws SQLException {
        Map<String, List<String>> byName = constraintsByColumns(c, """
                SELECT tc.CONSTRAINT_NAME AS name, kcu.COLUMN_NAME AS col
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                  ON kcu.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                 AND kcu.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
                WHERE tc.TABLE_SCHEMA = DATABASE()
                  AND tc.TABLE_NAME = 'workout_day'
                  AND tc.CONSTRAINT_TYPE = 'UNIQUE'
                ORDER BY tc.CONSTRAINT_NAME, kcu.COLUMN_NAME
                """);
        boolean compositeExists = false;
        for (Map.Entry<String, List<String>> e : byName.entrySet()) {
            List<String> cols = e.getValue();
            if (cols.size() == 1 && "day_number".equalsIgnoreCase(cols.get(0))) {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("ALTER TABLE workout_day DROP INDEX `" + e.getKey() + "`");
                    log.info("Dropped legacy unique index '{}' on workout_day.day_number", e.getKey());
                }
            } else if (isUserDay(cols)) {
                compositeExists = true;
            }
        }
        if (!compositeExists) {
            createComposite(c);
        }
    }

    /** H2 (local dev): constraint-backed indexes must be dropped via DROP CONSTRAINT. */
    private void ensureConstraintH2(Connection c) throws SQLException {
        Map<String, List<String>> byName = constraintsByColumns(c, """
                SELECT tc.CONSTRAINT_NAME AS name, ccu.COLUMN_NAME AS col
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE ccu
                  ON ccu.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                 AND ccu.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
                WHERE tc.TABLE_NAME = 'WORKOUT_DAY'
                  AND tc.CONSTRAINT_TYPE = 'UNIQUE'
                ORDER BY tc.CONSTRAINT_NAME, ccu.COLUMN_NAME
                """);
        boolean compositeExists = false;
        for (Map.Entry<String, List<String>> e : byName.entrySet()) {
            List<String> cols = e.getValue();
            if (cols.size() == 1 && "DAY_NUMBER".equalsIgnoreCase(cols.get(0))) {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("ALTER TABLE WORKOUT_DAY DROP CONSTRAINT \"" + e.getKey() + "\"");
                    log.info("Dropped legacy unique constraint '{}' on workout_day.day_number", e.getKey());
                }
            } else if (isUserDay(cols)) {
                compositeExists = true;
            }
        }
        if (!compositeExists) {
            createComposite(c);
        }
    }

    /**
     * Constraint name -> column list, for the given dialect query. The checks
     * below are order-insensitive (single day_number column vs. a two-column
     * user_id+day_number pair), so a portable ORDER BY COLUMN_NAME is used
     * instead of dialect-specific ordinal columns (e.g. H2 2.x's
     * CONSTRAINT_COLUMN_USAGE has no ORDINAL_POSITION).
     */
    private Map<String, List<String>> constraintsByColumns(Connection c, String sql) throws SQLException {
        Map<String, List<String>> byName = new LinkedHashMap<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                byName.computeIfAbsent(rs.getString("name"), k -> new ArrayList<>())
                        .add(rs.getString("col"));
            }
        }
        return byName;
    }

    /** "user_id,day_number" or "day_number,user_id" (column order varies by dialect). */
    private boolean isUserDay(List<String> cols) {
        return cols.size() == 2
                && cols.stream().anyMatch("user_id"::equalsIgnoreCase)
                && cols.stream().anyMatch("day_number"::equalsIgnoreCase);
    }

    private boolean isUserDay(String cols) {
        String normalized = cols.toLowerCase();
        return normalized.equals("user_id,day_number") || normalized.equals("day_number,user_id");
    }

    private void createComposite(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("ALTER TABLE workout_day ADD CONSTRAINT uk_workout_day_user_day "
                    + "UNIQUE (user_id, day_number)");
            log.info("Created unique constraint uk_workout_day_user_day (user_id, day_number)");
        }
    }

    // -------------------------------------------------------------- legacy

    /**
     * Claims the pre-auth legacy single-user world. Detection keys on "no PIN
     * set yet" rather than "no username": a crash after the username was
     * assigned (but before the copy/history re-point committed) must not strand
     * the legacy data on the next boot. The copy + re-point are one
     * transaction, so "the user already has a plan" is a reliable "already
     * migrated" signal.
     */
    private void migrateLegacyData() {
        List<AppUser> legacy = userRepository.findAllByPinHashIsNull();
        if (legacy.isEmpty()) {
            return;
        }
        AppUser user = legacy.get(0);
        if (user.getUsername() == null) {
            user.setUsername(uniqueUsername(sanitizeUsername(user.getName())));
            userRepository.save(user);
            log.info("Migrating legacy single-user data to username '{}' (existing data is kept)", user.getUsername());
        } else {
            log.info("Legacy single-user account '{}' found; checking whether it needs claiming", user.getUsername());
        }

        boolean alreadyMigrated = !workoutDayRepository.findAllByUserIdOrderByDayNumberAsc(user.getId()).isEmpty();
        if (alreadyMigrated) {
            log.info("Legacy data already claimed by '{}' — nothing to do", user.getUsername());
            return;
        }

        AuthService.TemplateCopy copy = authService.copyTemplateInto(user);

        List<WorkoutSession> sessions = sessionRepository.findAllByUserIdIsNull();
        for (WorkoutSession s : sessions) {
            s.setUserId(user.getId());
            WorkoutDay day = s.getWorkoutDay();
            if (day != null && day.getUserId() == null && copy.days().containsKey(day.getId())) {
                s.setWorkoutDay(copy.days().get(day.getId()));
            }
            for (SetLog sl : setLogRepository.findBySession(s)) {
                Exercise ex = sl.getExercise();
                if (ex != null && ex.getUserId() == null && copy.exercises().containsKey(ex.getId())) {
                    sl.setExercise(copy.exercises().get(ex.getId()));
                }
            }
            for (ExerciseNote n : exerciseNoteRepository.findBySession(s)) {
                Exercise ex = n.getExercise();
                if (ex != null && ex.getUserId() == null && copy.exercises().containsKey(ex.getId())) {
                    n.setExercise(copy.exercises().get(ex.getId()));
                }
            }
            sessionRepository.save(s);
        }

        for (BodyWeightLog l : bodyWeightRepository.findAllByUserIdIsNull()) {
            l.setUserId(user.getId());
            bodyWeightRepository.save(l);
        }
        log.info("Legacy migration complete: {} sessions claimed, {} sample exercises copied",
                sessions.size(), copy.exercises().size());
    }

    private String sanitizeUsername(String name) {
        String base = name == null ? "" : name.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "");
        if (base.length() < 2) {
            base = "athlete";
        }
        return base.length() > 30 ? base.substring(0, 30) : base;
    }

    private String uniqueUsername(String base) {
        String candidate = base;
        int n = 1;
        while (userRepository.findByUsername(candidate).isPresent()) {
            String suffix = String.valueOf(n++);
            candidate = base.substring(0, Math.min(base.length(), 30 - suffix.length())) + suffix;
        }
        return candidate;
    }
}
