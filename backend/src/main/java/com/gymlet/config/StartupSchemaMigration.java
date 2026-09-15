package com.gymlet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * JDBC-only schema fixes that must run before any repository-based startup
 * runner can load entities from existing production tables.
 */
@Component
@Order(0)
public class StartupSchemaMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSchemaMigration.class);

    private final DataSource dataSource;

    public StartupSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        ensureWorkoutDayRestDayColumn();
    }

    private void ensureWorkoutDayRestDayColumn() {
        try (Connection c = dataSource.getConnection()) {
            String product = c.getMetaData().getDatabaseProductName().toLowerCase();
            if (columnExists(c, "workout_day", "rest_day")) {
                return;
            }
            try (Statement st = c.createStatement()) {
                if (product.contains("postgres")) {
                    st.executeUpdate("ALTER TABLE workout_day ADD COLUMN IF NOT EXISTS rest_day BOOLEAN NOT NULL DEFAULT FALSE");
                } else if (product.contains("mysql") || product.contains("mariadb")) {
                    st.executeUpdate("ALTER TABLE workout_day ADD COLUMN rest_day BOOLEAN NOT NULL DEFAULT FALSE");
                } else {
                    st.executeUpdate("ALTER TABLE workout_day ADD COLUMN IF NOT EXISTS rest_day BOOLEAN NOT NULL DEFAULT FALSE");
                }
                log.info("Added workout_day.rest_day with default false for existing rows");
            }
        } catch (Exception e) {
            log.error("Could not ensure workout_day.rest_day column: {}", e.getMessage(), e);
            throw new IllegalStateException("workout_day.rest_day migration failed", e);
        }
    }

    private boolean columnExists(Connection c, String tableName, String columnName) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        String schema = c.getSchema();
        return columnExists(meta, schema, tableName, columnName)
                || columnExists(meta, schema, tableName.toUpperCase(), columnName.toUpperCase())
                || columnExists(meta, null, tableName, columnName)
                || columnExists(meta, null, tableName.toUpperCase(), columnName.toUpperCase());
    }

    private boolean columnExists(DatabaseMetaData meta, String schema, String tableName, String columnName)
            throws SQLException {
        try (ResultSet rs = meta.getColumns(null, schema, tableName, columnName)) {
            return rs.next();
        }
    }
}
