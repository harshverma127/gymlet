package com.gymlet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Enforces the "at most one row per {@code (user_id, plan_id, weekday)}" invariant
 * at the database level, so the schedule corruption this app used to accumulate
 * cannot be re-created by any code path.
 *
 * <p>Runs at {@code @Order(3)} — deliberately <em>after</em>
 * {@link SchemaMigration} ({@code @Order(2)}), whose {@link com.gymlet.service.ScheduleReconciler}
 * pass merges duplicates inside a transaction. Because this runner opens its own
 * connection, it must run once that transaction has committed; otherwise it would
 * only see the pre-repair data. If duplicates still remain it skips (and logs)
 * rather than failing startup, and retries on the next boot.
 *
 * <p>PostgreSQL gets a partial unique index ({@code WHERE weekday IS NOT NULL}) so
 * unassigned rows stay exempt. MySQL/MariaDB and H2 lack partial indexes, but every
 * supported engine treats NULL as distinct in a unique index, so a plain composite
 * index is equivalent there. No data is ever modified by this runner.
 */
@Component
@Order(3)
public class WeekdayIndexMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WeekdayIndexMigration.class);

    private static final String INDEX_NAME = "uk_workout_day_user_plan_weekday";

    private final DataSource dataSource;

    public WeekdayIndexMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection c = dataSource.getConnection()) {
            String product = c.getMetaData().getDatabaseProductName().toLowerCase();
            if (hasDuplicateWeekdays(c)) {
                log.warn("Skipping unique weekday index: duplicate (user_id, plan_id, weekday) rows still exist. "
                        + "The schedule reconciler will retry on the next boot.");
                return;
            }
            if (product.contains("postgres")) {
                execute(c, "CREATE UNIQUE INDEX IF NOT EXISTS " + INDEX_NAME
                        + " ON workout_day (user_id, plan_id, weekday) WHERE weekday IS NOT NULL");
            } else if (product.contains("mysql") || product.contains("mariadb")) {
                if (!indexExistsInfoSchema(c, "workout_day", INDEX_NAME)) {
                    execute(c, "CREATE UNIQUE INDEX " + INDEX_NAME
                            + " ON workout_day (user_id, plan_id, weekday)");
                }
            } else {
                execute(c, "CREATE UNIQUE INDEX IF NOT EXISTS " + INDEX_NAME
                        + " ON workout_day (user_id, plan_id, weekday)");
            }
        } catch (SQLException e) {
            // A duplicate that slipped through must surface, not be swallowed.
            log.error("Could not ensure unique weekday index: {}", e.getMessage(), e);
            throw new IllegalStateException("workout_day weekday uniqueness fix failed", e);
        }
    }

    private boolean hasDuplicateWeekdays(Connection c) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM (
                    SELECT user_id, plan_id, weekday
                    FROM workout_day
                    WHERE weekday IS NOT NULL
                    GROUP BY user_id, plan_id, weekday
                    HAVING COUNT(*) > 1
                ) dupes
                """;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() && rs.getLong(1) > 0;
        }
    }

    private boolean indexExistsInfoSchema(Connection c, String table, String index) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                """;
        try (java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, index);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        }
    }

    private void execute(Connection c, String ddl) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(ddl);
            log.info("Ensured unique weekday index '{}' on workout_day (user_id, plan_id, weekday)", INDEX_NAME);
        }
    }
}
