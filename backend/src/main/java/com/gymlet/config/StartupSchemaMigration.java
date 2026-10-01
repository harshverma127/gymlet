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
import java.util.List;

/**
 * JDBC-only schema fixes that must run before any repository-based startup
 * runner can load entities from existing production tables.
 *
 * <p>Hibernate's {@code ddl-auto=update} is intentionally NOT relied upon for
 * these: adding a {@code NOT NULL} column to a populated PostgreSQL table can
 * fail (leaving the column missing) while startup continues, which is exactly
 * how the production error {@code column wd1_0.rest_day does not exist}
 * happens. Every column below is added explicitly and idempotently, with a
 * default for existing rows where needed. No column is ever dropped and no
 * table is ever recreated.
 */
@Component
@Order(0)
public class StartupSchemaMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSchemaMigration.class);

    /** (table, column, SQL type + optional default) for every post-launch column. */
    private static final List<ColumnSpec> COLUMNS = List.of(
            new ColumnSpec("workout_day", "rest_day", "BOOLEAN", " NOT NULL DEFAULT FALSE"),
            new ColumnSpec("workout_day", "plan_id", "BIGINT", ""),
            new ColumnSpec("workout_day", "weekday", "INTEGER", ""),
            new ColumnSpec("workout_session", "workout_day_name_snapshot", "VARCHAR(255)", ""),
            new ColumnSpec("workout_exercise", "target_rir", "INTEGER", ""),
            new ColumnSpec("workout_exercise", "rest_seconds", "INTEGER", ""),
            new ColumnSpec("app_user", "active_plan_id", "BIGINT", ""),
            new ColumnSpec("app_user", "training_profile", "TEXT", "")
    );

    private final DataSource dataSource;

    public StartupSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection c = dataSource.getConnection()) {
            String product = c.getMetaData().getDatabaseProductName().toLowerCase();
            for (ColumnSpec spec : COLUMNS) {
                ensureColumn(c, product, spec);
            }
        } catch (Exception e) {
            log.error("Could not ensure required columns: {}", e.getMessage(), e);
            throw new IllegalStateException("schema column migration failed", e);
        }
    }

    private void ensureColumn(Connection c, String product, ColumnSpec spec) throws SQLException {
        if (!tableExists(c, spec.table())) {
            return;
        }
        if (columnExists(c, spec.table(), spec.column())) {
            return;
        }
        String ddl = "ALTER TABLE " + spec.table() + " ADD COLUMN "
                + spec.column() + " " + spec.type() + spec.defaultClause();
        if (product.contains("postgres")) {
            ddl = "ALTER TABLE " + spec.table() + " ADD COLUMN IF NOT EXISTS "
                    + spec.column() + " " + spec.type() + spec.defaultClause();
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate(ddl);
            log.info("Added {}.{} ({}{}) for existing rows", spec.table(), spec.column(),
                    spec.type(), spec.defaultClause());
        }
    }

    private boolean tableExists(Connection c, String tableName) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        return tableExists(meta, null, tableName)
                || tableExists(meta, null, tableName.toUpperCase())
                || tableExists(meta, c.getSchema(), tableName)
                || tableExists(meta, c.getSchema(), tableName.toUpperCase());
    }

    private boolean tableExists(DatabaseMetaData meta, String schema, String tableName) throws SQLException {
        try (ResultSet rs = meta.getTables(null, schema, tableName, null)) {
            return rs.next();
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

    private record ColumnSpec(String table, String column, String type, String defaultClause) {
    }
}
