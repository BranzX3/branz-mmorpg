package com.branz.mmorpg.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
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
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .validateMigrationNaming(true)
                    .load()
                    .migrate();
            return new DatabaseManager(dataSource);
        } catch (RuntimeException exception) {
            dataSource.close();
            throw exception;
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
