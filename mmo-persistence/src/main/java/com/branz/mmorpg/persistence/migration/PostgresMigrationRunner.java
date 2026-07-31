package com.branz.mmorpg.persistence.migration;

import com.branz.mmorpg.api.result.Result;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;

/** Forward-only PostgreSQL migration runner with transactional locking and checksum enforcement. */
public final class PostgresMigrationRunner {
    private static final long MIGRATION_LOCK_ID = 0x4252414E5A4D4D4FL;
    private static final String CREATE_HISTORY =
            """
            CREATE TABLE IF NOT EXISTS mmo_schema_migrations (
                version INTEGER PRIMARY KEY,
                description TEXT NOT NULL,
                checksum CHAR(64) NOT NULL,
                installed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """;

    private final DataSource dataSource;

    public PostgresMigrationRunner(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public Result<MigrationReport, MigrationErrorCode> migrate(MigrationCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        MigrationErrorCode sqlFailure = MigrationErrorCode.MIGRATION_DATABASE_UNAVAILABLE;
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                sqlFailure = MigrationErrorCode.MIGRATION_LOCK_FAILED;
                acquireLock(connection);
                sqlFailure = MigrationErrorCode.MIGRATION_APPLY_FAILED;
                ensureHistoryTable(connection);
                Map<Integer, AppliedMigration> applied = readApplied(connection);
                Result<MigrationReport, MigrationErrorCode> validation =
                        validateApplied(catalog, applied);
                if (validation instanceof Result.Failure<MigrationReport, MigrationErrorCode>) {
                    connection.rollback();
                    return validation;
                }

                List<Integer> installed = new ArrayList<>();
                for (SqlMigration migration : catalog.migrations()) {
                    if (!applied.containsKey(migration.version())) {
                        apply(connection, migration);
                        installed.add(migration.version());
                    }
                }
                connection.commit();
                int currentVersion =
                        catalog.migrations().stream()
                                .mapToInt(SqlMigration::version)
                                .max()
                                .orElse(0);
                return Result.success(new MigrationReport(currentVersion, installed));
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                return Result.failure(sqlFailure, sqlDetail(exception));
            } finally {
                restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (SQLException exception) {
            return Result.failure(
                    MigrationErrorCode.MIGRATION_DATABASE_UNAVAILABLE, sqlDetail(exception));
        }
    }

    private static void acquireLock(Connection connection) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            statement.setLong(1, MIGRATION_LOCK_ID);
            statement.execute();
        }
    }

    private static void ensureHistoryTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_HISTORY);
        }
    }

    private static Map<Integer, AppliedMigration> readApplied(Connection connection)
            throws SQLException {
        LinkedHashMap<Integer, AppliedMigration> applied = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                """
                                SELECT version, description, checksum
                                FROM mmo_schema_migrations
                                ORDER BY version
                                """)) {
            while (rows.next()) {
                applied.put(
                        rows.getInt("version"),
                        new AppliedMigration(
                                rows.getString("description"), rows.getString("checksum").trim()));
            }
        }
        return applied;
    }

    private static Result<MigrationReport, MigrationErrorCode> validateApplied(
            MigrationCatalog catalog, Map<Integer, AppliedMigration> applied) {
        Map<Integer, SqlMigration> expected = new LinkedHashMap<>();
        catalog.migrations().forEach(migration -> expected.put(migration.version(), migration));
        for (Map.Entry<Integer, AppliedMigration> entry : applied.entrySet()) {
            SqlMigration migration = expected.get(entry.getKey());
            if (migration == null) {
                return Result.failure(
                        MigrationErrorCode.MIGRATION_UNKNOWN_APPLIED,
                        "Database contains unknown migration version: " + entry.getKey());
            }
            if (!migration.checksum().equals(entry.getValue().checksum())) {
                return Result.failure(
                        MigrationErrorCode.MIGRATION_CHECKSUM_MISMATCH,
                        "Checksum mismatch for migration version: " + entry.getKey());
            }
        }
        int currentVersion = applied.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        return Result.success(new MigrationReport(currentVersion, List.of()));
    }

    private static void apply(Connection connection, SqlMigration migration) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(migration.sql());
        }
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO mmo_schema_migrations(version, description, checksum)
                        VALUES (?, ?, ?)
                        """)) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.description());
            statement.setString(3, migration.checksum());
            statement.executeUpdate();
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The original database failure is the actionable result.
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
            // Closing the connection is the only safe recovery left.
        }
    }

    private static String sqlDetail(SQLException exception) {
        String sqlState = exception.getSQLState();
        return exception.getClass().getSimpleName()
                + (sqlState == null ? "" : " SQLSTATE=" + sqlState);
    }

    private record AppliedMigration(String description, String checksum) {}
}
