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
import com.gymlet.service.PlanService;
import com.gymlet.service.ScheduleReconciler;
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
 * One-time, idempotent migration of the pre-multi-user / pre-plan database.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Make {@code workout_day} uniqueness coherent with the plan system: the
 *       single correct key is {@code (user_id, plan_id, day_number)}. Any legacy
 *       unique constraint/index on {@code (user_id, day_number)} or on
 *       {@code day_number} alone is dropped, because it silently prevents two
 *       plans from each having a "Day 1" (and crashes plan creation/copy).</li>
 *   <li>Claim the legacy single-user world (an AppUser with no PIN, plus any
 *       workouts/history/bodyweight owned by nobody).</li>
 *   <li>Reconcile every user's plan + 7-day schedule via PlanService, which is
 *       the single source of truth in the application.</li>
 * </ol>
 *
 * Existing data is never deleted, and every step is safe to re-run against an
 * existing PostgreSQL database. DDL is executed directly (not left to
 * Hibernate {@code ddl-auto}) so ordering is deterministic.
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
    private final PlanService planService;
    private final ScheduleReconciler scheduleReconciler;

    public SchemaMigration(DataSource dataSource,
                           AppUserRepository userRepository,
                           WorkoutDayRepository workoutDayRepository,
                           WorkoutSessionRepository sessionRepository,
                           SetLogRepository setLogRepository,
                           ExerciseNoteRepository exerciseNoteRepository,
                           BodyWeightLogRepository bodyWeightRepository,
                           AuthService authService,
                           PlanService planService,
                           ScheduleReconciler scheduleReconciler) {
        this.dataSource = dataSource;
        this.userRepository = userRepository;
        this.workoutDayRepository = workoutDayRepository;
        this.sessionRepository = sessionRepository;
        this.setLogRepository = setLogRepository;
        this.exerciseNoteRepository = exerciseNoteRepository;
        this.bodyWeightRepository = bodyWeightRepository;
        this.authService = authService;
        this.planService = planService;
        this.scheduleReconciler = scheduleReconciler;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureWorkoutDayUniqueness();
        migrateLegacyData();
        // Repair already-planned malformed schedules before ensurePlanForUser
        // touches them, then reconcile again afterwards so anything the legacy
        // backfill produced is normalised too. Both passes are idempotent.
        scheduleReconciler.reconcileAll();
        reconcilePlans();
        scheduleReconciler.reconcileAll();
    }

    // ----------------------------------------------------------- uniqueness

    /**
     * Ensures {@code workout_day} is unique on {@code (user_id, plan_id, day_number)}
     * and drops every legacy unique key that is incompatible with multiple plans.
     * Runs on every boot and is a no-op once the schema is correct.
     */
    private void ensureWorkoutDayUniqueness() {
        try (Connection c = dataSource.getConnection()) {
            String product = c.getMetaData().getDatabaseProductName().toLowerCase();
            if (product.contains("postgres")) {
                ensureUniquenessPostgres(c);
            } else if (product.contains("mysql") || product.contains("mariadb")) {
                ensureUniquenessInfoSchema(c, true);
            } else {
                ensureUniquenessInfoSchema(c, false);
            }
        } catch (Exception e) {
            // Never hide a schema problem: continuing with an incompatible old
            // constraint would silently break plan creation for every user.
            log.error("Could not reconcile workout_day uniqueness: {}", e.getMessage(), e);
            throw new IllegalStateException("workout_day uniqueness fix failed", e);
        }
    }

    /**
     * PostgreSQL: unique constraints are table constraints, so the constraint
     * itself must be dropped (DROP INDEX fails with "constraint ... requires it").
     */
    private void ensureUniquenessPostgres(Connection c) throws SQLException {
        boolean planCompositeExists = false;
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
                List<String> cols = splitColumns(rs.getString("cols"));
                if (isPlanDayKey(cols)) {
                    planCompositeExists = true;
                } else if (isLegacyDayKey(cols)) {
                    toDrop.add(rs.getString("name"));
                }
            }
        }
        for (String name : toDrop) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("ALTER TABLE workout_day DROP CONSTRAINT \"" + name + "\"");
                log.info("Dropped legacy unique constraint '{}' on workout_day (incompatible with multiple plans)", name);
            }
        }

        // Any leftover unique index that is not constraint-backed (day_number alone
        // or user_id+day_number) must also go.
        for (String name : orphanUniqueIndexesPostgres(c)) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DROP INDEX IF EXISTS \"" + name + "\"");
                log.info("Dropped legacy unique index '{}' on workout_day", name);
            }
        }

        if (!planCompositeExists) {
            createPlanDayConstraint(c, "ALTER TABLE workout_day ADD CONSTRAINT uk_workout_day_user_plan_day "
                    + "UNIQUE (user_id, plan_id, day_number)");
        }
    }

    private List<String> orphanUniqueIndexesPostgres(Connection c) throws SQLException {
        List<String> names = new ArrayList<>();
        String single = """
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
                """;
        String pair = """
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
                  AND i.indnkeyatts = 2
                  AND i.indkey[0] IN (SELECT attnum FROM pg_attribute
                                      WHERE attrelid = i.indrelid AND attname IN ('user_id', 'day_number'))
                  AND i.indkey[1] IN (SELECT attnum FROM pg_attribute
                                      WHERE attrelid = i.indrelid AND attname IN ('user_id', 'day_number'))
                """;
        for (String sql : List.of(single, pair)) {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    names.add(rs.getString("name"));
                }
            }
        }
        return names;
    }

    /**
     * MySQL/MariaDB and H2: a unique constraint IS its index, so DROP INDEX /
     * DROP CONSTRAINT removes both. {@code mysql} only changes the DROP syntax.
     */
    private void ensureUniquenessInfoSchema(Connection c, boolean mysql) throws SQLException {
        String sql = mysql
                ? """
                SELECT tc.CONSTRAINT_NAME AS name, kcu.COLUMN_NAME AS col
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                  ON kcu.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                 AND kcu.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
                WHERE tc.TABLE_SCHEMA = DATABASE()
                  AND tc.TABLE_NAME = 'workout_day'
                  AND tc.CONSTRAINT_TYPE = 'UNIQUE'
                ORDER BY tc.CONSTRAINT_NAME, kcu.COLUMN_NAME
                """
                : """
                SELECT tc.CONSTRAINT_NAME AS name, ccu.COLUMN_NAME AS col
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE ccu
                  ON ccu.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                 AND ccu.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
                WHERE tc.TABLE_NAME = 'WORKOUT_DAY'
                  AND tc.CONSTRAINT_TYPE = 'UNIQUE'
                ORDER BY tc.CONSTRAINT_NAME, ccu.COLUMN_NAME
                """;

        Map<String, List<String>> byName = new LinkedHashMap<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                byName.computeIfAbsent(rs.getString("name"), k -> new ArrayList<>())
                        .add(rs.getString("col"));
            }
        }

        boolean planCompositeExists = false;
        for (Map.Entry<String, List<String>> e : byName.entrySet()) {
            List<String> cols = e.getValue();
            if (isPlanDayKey(cols)) {
                planCompositeExists = true;
            } else if (isLegacyDayKey(cols)) {
                try (Statement st = c.createStatement()) {
                    if (mysql) {
                        st.executeUpdate("ALTER TABLE workout_day DROP INDEX `" + e.getKey() + "`");
                    } else {
                        st.executeUpdate("ALTER TABLE WORKOUT_DAY DROP CONSTRAINT \"" + e.getKey() + "\"");
                    }
                    log.info("Dropped legacy unique key '{}' on workout_day (incompatible with multiple plans)", e.getKey());
                }
            }
        }
        if (!planCompositeExists) {
            createPlanDayConstraint(c, "ALTER TABLE workout_day ADD CONSTRAINT uk_workout_day_user_plan_day "
                    + "UNIQUE (user_id, plan_id, day_number)");
        }
    }

    /** The correct key: exactly {user_id, plan_id, day_number}. */
    private boolean isPlanDayKey(List<String> cols) {
        return cols.size() == 3
                && cols.stream().anyMatch("user_id"::equalsIgnoreCase)
                && cols.stream().anyMatch("plan_id"::equalsIgnoreCase)
                && cols.stream().anyMatch("day_number"::equalsIgnoreCase);
    }

    /** Legacy keys that break multiple plans: {day_number} or {user_id, day_number}. */
    private boolean isLegacyDayKey(List<String> cols) {
        if (cols.size() == 1) {
            return "day_number".equalsIgnoreCase(cols.get(0));
        }
        return cols.size() == 2
                && cols.stream().anyMatch("user_id"::equalsIgnoreCase)
                && cols.stream().anyMatch("day_number"::equalsIgnoreCase);
    }

    private List<String> splitColumns(String csv) {
        List<String> cols = new ArrayList<>();
        if (csv == null) {
            return cols;
        }
        for (String part : csv.split(",")) {
            if (!part.isBlank()) {
                cols.add(part.trim());
            }
        }
        return cols;
    }

    private void createPlanDayConstraint(Connection c, String ddl) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(ddl);
            log.info("Created unique constraint uk_workout_day_user_plan_day (user_id, plan_id, day_number)");
        }
    }

    // ------------------------------------------------------------- plans

    /**
     * Ensures every user has an active plan and a coherent 7-day schedule.
     * Delegates to {@link PlanService#ensurePlanForUser(AppUser)} so the boot
     * path and the registration path share exactly one implementation.
     */
    private void reconcilePlans() {
        for (AppUser user : userRepository.findAll()) {
            planService.ensurePlanForUser(user);
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
