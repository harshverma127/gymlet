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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * One-time, idempotent migration of the pre-multi-user database:
 *
 *  1. workout_day.day_number used to be globally unique; per-user plans collide,
 *     so any old unique index on that column is dropped (a per-user composite
 *     unique is defined on the entity instead).
 *  2. The legacy single-user world (one AppUser with no credentials, plus any
 *     workouts/history/bodyweight owned by nobody) is claimed: the legacy user
 *     gets a username, gets an independent copy of the default split, and all
 *     existing history is re-pointed to that copy.
 *
 * Existing data is never deleted.
 */
@Component
@Order(2)
public class SchemaMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigration.class);

    private final DataSource dataSource;
    private final AppUserRepository userRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final SetLogRepository setLogRepository;
    private final ExerciseNoteRepository exerciseNoteRepository;
    private final BodyWeightLogRepository bodyWeightRepository;
    private final AuthService authService;

    public SchemaMigration(DataSource dataSource,
                           AppUserRepository userRepository,
                           WorkoutSessionRepository sessionRepository,
                           SetLogRepository setLogRepository,
                           ExerciseNoteRepository exerciseNoteRepository,
                           BodyWeightLogRepository bodyWeightRepository,
                           AuthService authService) {
        this.dataSource = dataSource;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.setLogRepository = setLogRepository;
        this.exerciseNoteRepository = exerciseNoteRepository;
        this.bodyWeightRepository = bodyWeightRepository;
        this.authService = authService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        dropLegacyDayNumberUniqueIndex();
        migrateLegacyData();
    }

    // ------------------------------------------------------------ constraint

    private void dropLegacyDayNumberUniqueIndex() {
        try (Connection c = dataSource.getConnection()) {
            String product = c.getMetaData().getDatabaseProductName().toLowerCase();
            List<String> indexes = new ArrayList<>();
            try (Statement st = c.createStatement()) {
                ResultSet rs;
                if (product.contains("postgres")) {
                    rs = st.executeQuery("SELECT indexname FROM pg_indexes WHERE tablename = 'workout_day'"
                            + " AND indexdef ILIKE '%day_number%' AND indexdef NOT ILIKE '%user_id%'");
                } else if (product.contains("mysql") || product.contains("mariadb")) {
                    rs = st.executeQuery("SELECT DISTINCT s.INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS s"
                            + " WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'workout_day'"
                            + " AND s.COLUMN_NAME = 'day_number'"
                            + " AND s.INDEX_NAME NOT IN (SELECT t.INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS t"
                            + " WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'workout_day' AND t.COLUMN_NAME = 'user_id')");
                } else {
                    // H2 (2.x) and other embedded DBs: column lists live in INDEX_COLUMNS
                    rs = st.executeQuery("SELECT DISTINCT i.INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES i"
                            + " JOIN INFORMATION_SCHEMA.INDEX_COLUMNS c"
                            + "   ON c.INDEX_NAME = i.INDEX_NAME AND c.INDEX_SCHEMA = i.INDEX_SCHEMA"
                            + " WHERE i.TABLE_NAME = 'WORKOUT_DAY' AND c.COLUMN_NAME = 'DAY_NUMBER'"
                            + " AND i.INDEX_NAME NOT IN (SELECT ic.INDEX_NAME FROM INFORMATION_SCHEMA.INDEX_COLUMNS ic"
                            + " WHERE ic.TABLE_NAME = 'WORKOUT_DAY' AND ic.COLUMN_NAME = 'USER_ID')");
                }
                while (rs.next()) {
                    indexes.add(rs.getString(1));
                }
            }
            for (String index : indexes) {
                try (Statement st = c.createStatement()) {
                    if (product.contains("mysql") || product.contains("mariadb")) {
                        st.executeUpdate("ALTER TABLE workout_day DROP INDEX `" + index + "`");
                    } else {
                        st.executeUpdate("DROP INDEX \"" + index + "\"");
                    }
                    log.info("Dropped legacy unique index '{}' on workout_day.day_number", index);
                }
            }
        } catch (Exception e) {
            log.warn("Could not check/drop legacy day_number unique index (continuing): {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------- legacy

    private void migrateLegacyData() {
        List<AppUser> legacy = userRepository.findAllByUsernameIsNull();
        if (legacy.isEmpty()) {
            return;
        }
        AppUser user = legacy.get(0);
        user.setUsername(uniqueUsername(sanitizeUsername(user.getName())));
        userRepository.save(user);
        log.info("Migrating legacy single-user data to username '{}' (existing data is kept)", user.getUsername());

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
