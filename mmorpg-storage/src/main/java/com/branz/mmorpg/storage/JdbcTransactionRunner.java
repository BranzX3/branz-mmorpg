package com.branz.mmorpg.storage;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.runtime.TransactionRunner;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * JDBC-backed {@link TransactionRunner}, the bridge that lets core game rules
 * demand atomicity without seeing a {@link Connection}.
 *
 * <p>Storage-layer callers unwrap the context back to a {@code Connection};
 * core-layer callers pass it through untouched.
 */
public final class JdbcTransactionRunner implements TransactionRunner {

    private final DatabaseManager databaseManager;

    public JdbcTransactionRunner(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public <T> T inTransaction(TransactionalWork<T> work) {
        Objects.requireNonNull(work, "work");
        try {
            return databaseManager.inTransaction(connection -> work.apply(new JdbcContext(connection)));
        } catch (SQLException exception) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE, "transaction failed", exception);
        }
    }

    private record JdbcContext(Connection connection) implements TransactionContext {

        @Override
        public <T> T unwrap(Class<T> type) {
            Objects.requireNonNull(type, "type");
            if (!type.isInstance(connection)) {
                throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                        "transaction context is a JDBC Connection, not " + type.getName());
            }
            return type.cast(connection);
        }
    }
}
