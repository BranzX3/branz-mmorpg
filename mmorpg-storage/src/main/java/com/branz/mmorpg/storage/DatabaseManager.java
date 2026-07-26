package com.branz.mmorpg.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

public final class DatabaseManager implements AutoCloseable {
    private final HikariDataSource dataSource;

    private DatabaseManager(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static DatabaseManager connect(DatabaseConfig config) {
        Objects.requireNonNull(config, "config");
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("BranzMMORPG");
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.maximumPoolSize());
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(config.connectionTimeoutMillis());
        hikari.setAutoCommit(false);
        hikari.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        HikariDataSource dataSource = new HikariDataSource(hikari);
        try {
            migrateLegacyHistoryTable(dataSource);
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    // The network database is shared with BranzWallet and Branz
                    // Idle, so the default flyway_schema_history name is not ours
                    // to claim.
                    .table("mmorpg_schema_history")
                    .validateMigrationNaming(true)
                    .load()
                    .migrate();
            return new DatabaseManager(dataSource);
        } catch (RuntimeException exception) {
            dataSource.close();
            throw exception;
        }
    }

    /**
     * Moves the pre-merge Flyway history to the project-specific name.
     *
     * <p>The legacy table is claimed only when it contains the exact Player
     * Session V2 migration. A shared history owned by another plugin is left
     * untouched and Flyway will fail closed instead of adopting foreign state.
     */
    private static void migrateLegacyHistoryTable(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            if (tableExists(connection, "mmorpg_schema_history")
                    || !tableExists(connection, "flyway_schema_history")
                    || !isLegacyBranzHistory(connection)) {
                connection.rollback();
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "RENAME TABLE flyway_schema_history TO mmorpg_schema_history");
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to migrate legacy Branz MMORPG Flyway history", exception);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        }
    }

    private static boolean isLegacyBranzHistory(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '2' AND script = 'V2__player_profiles.sql' AND success = TRUE")) {
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        }
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public <T> T inTransaction(SqlWork<T> work) throws SQLException {
        Objects.requireNonNull(work, "work");
        try (Connection connection = dataSource.getConnection()) {
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Exception exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                if (exception instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw new SQLException("Database transaction failed", exception);
            }
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }

    @FunctionalInterface
    public interface SqlWork<T> {
        T execute(Connection connection) throws Exception;
    }
}
