package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.SessionId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/** Blocking PostgreSQL repository; callers must keep it off Paper server threads. */
public final class JdbcTransactionJournalRepository implements TransactionJournalRepository {
    private static final String COLUMNS =
            """
            transaction_id, idempotency_key, character_id, session_id,
            operation_type, state, reserved_inputs, intended_outputs,
            content_version, created_at, updated_at
            """;

    private final DataSource dataSource;

    public JdbcTransactionJournalRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Result<JournalPrepareOutcome, TransactionErrorCode> prepare(TransactionRequest request) {
        Objects.requireNonNull(request, "request");
        return inTransaction(connection -> prepare(connection, request));
    }

    @Override
    public Result<JournalTransitionOutcome, TransactionErrorCode> transition(
            TransactionId transactionId, TransactionState targetState) {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(targetState, "targetState");
        if (targetState == TransactionState.PREPARED) {
            return Result.failure(
                    TransactionErrorCode.TRANSACTION_INVALID_STATE,
                    "PREPARED is created through prepare, not a state transition.");
        }
        return inTransaction(connection -> transition(connection, transactionId, targetState));
    }

    @Override
    public Result<Optional<TransactionJournalEntry>, TransactionErrorCode> find(
            TransactionId transactionId) {
        Objects.requireNonNull(transactionId, "transactionId");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT "
                                        + COLUMNS
                                        + " FROM transaction_journal "
                                        + "WHERE transaction_id = ?")) {
            statement.setObject(1, transactionId.value());
            return Result.success(queryOptional(statement));
        } catch (SQLException exception) {
            return failure(exception);
        }
    }

    @Override
    public Result<Optional<TransactionJournalEntry>, TransactionErrorCode> findByIdempotencyKey(
            String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT "
                                        + COLUMNS
                                        + " FROM transaction_journal "
                                        + "WHERE idempotency_key = ?")) {
            statement.setString(1, idempotencyKey);
            return Result.success(queryOptional(statement));
        } catch (SQLException exception) {
            return failure(exception);
        }
    }

    static Result<JournalPrepareOutcome, TransactionErrorCode> prepare(
            Connection connection, TransactionRequest request) {
        try {
            TransactionJournalEntry inserted = insert(connection, request);
            if (inserted != null) {
                return Result.success(new JournalPrepareOutcome(inserted, true));
            }
            ExistingRequest existing = selectByIdempotencyKeyForUpdate(connection, request);
            if (existing == null) {
                return Result.failure(
                        TransactionErrorCode.TRANSACTION_DATABASE_UNAVAILABLE,
                        "Idempotency conflict row disappeared during preparation.");
            }
            if (!existing.matchesRequest()) {
                return Result.failure(
                        TransactionErrorCode.TRANSACTION_IDEMPOTENCY_CONFLICT,
                        "Idempotency key belongs to a different operation.");
            }
            return Result.success(new JournalPrepareOutcome(existing.entry(), false));
        } catch (SQLException exception) {
            return failure(exception);
        }
    }

    static Result<JournalTransitionOutcome, TransactionErrorCode> transition(
            Connection connection, TransactionId transactionId, TransactionState targetState) {
        try {
            TransactionJournalEntry current =
                    selectByTransactionIdForUpdate(connection, transactionId);
            if (current == null) {
                return Result.failure(
                        TransactionErrorCode.TRANSACTION_NOT_FOUND,
                        "Transaction journal entry does not exist.");
            }
            if (current.state() == targetState) {
                return Result.success(new JournalTransitionOutcome(current, false));
            }
            if (current.state() != TransactionState.PREPARED
                    || targetState == TransactionState.PREPARED) {
                return Result.failure(
                        TransactionErrorCode.TRANSACTION_INVALID_STATE,
                        "A terminal transaction state cannot be replaced.");
            }
            return Result.success(
                    new JournalTransitionOutcome(
                            updateState(connection, transactionId, targetState), true));
        } catch (SQLException exception) {
            return failure(exception);
        }
    }

    private static TransactionJournalEntry insert(Connection connection, TransactionRequest request)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO transaction_journal(
                            transaction_id, idempotency_key, character_id, session_id,
                            operation_type, state, reserved_inputs, intended_outputs,
                            content_version, created_at, updated_at
                        )
                        VALUES (
                            ?, ?, ?, ?, ?, 'PREPARED',
                            CAST(? AS JSONB), CAST(? AS JSONB), ?,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        ON CONFLICT (idempotency_key) DO NOTHING
                        RETURNING
                        """
                                + COLUMNS)) {
            bindRequest(statement, request);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? readEntry(row) : null;
            }
        }
    }

    private static ExistingRequest selectByIdempotencyKeyForUpdate(
            Connection connection, TransactionRequest request) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT "
                                + COLUMNS
                                + """
                                ,
                                character_id IS NOT DISTINCT FROM ? AS same_character,
                                session_id IS NOT DISTINCT FROM ? AS same_session,
                                operation_type = ? AS same_operation,
                                reserved_inputs = CAST(? AS JSONB) AS same_inputs,
                                intended_outputs = CAST(? AS JSONB) AS same_outputs,
                                content_version = ? AS same_content
                                FROM transaction_journal
                                WHERE idempotency_key = ?
                                FOR UPDATE
                                """)) {
            setNullableUuid(statement, 1, request.characterId().map(CharacterId::value));
            setNullableUuid(statement, 2, request.sessionId().map(SessionId::value));
            statement.setString(3, request.operationType());
            statement.setString(4, request.reservedInputsJson());
            statement.setString(5, request.intendedOutputsJson());
            statement.setString(6, request.contentVersion());
            statement.setString(7, request.idempotencyKey());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return null;
                }
                boolean matches =
                        row.getBoolean("same_character")
                                && row.getBoolean("same_session")
                                && row.getBoolean("same_operation")
                                && row.getBoolean("same_inputs")
                                && row.getBoolean("same_outputs")
                                && row.getBoolean("same_content");
                return new ExistingRequest(readEntry(row), matches);
            }
        }
    }

    private static TransactionJournalEntry selectByTransactionIdForUpdate(
            Connection connection, TransactionId transactionId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT "
                                + COLUMNS
                                + " FROM transaction_journal "
                                + "WHERE transaction_id = ? FOR UPDATE")) {
            statement.setObject(1, transactionId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? readEntry(row) : null;
            }
        }
    }

    private static TransactionJournalEntry updateState(
            Connection connection, TransactionId transactionId, TransactionState targetState)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "UPDATE transaction_journal "
                                + "SET state = CAST(? AS mmo_transaction_state), "
                                + "updated_at = CURRENT_TIMESTAMP "
                                + "WHERE transaction_id = ? RETURNING "
                                + COLUMNS)) {
            statement.setString(1, targetState.name());
            statement.setObject(2, transactionId.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("Transaction state update did not return a row.");
                }
                return readEntry(row);
            }
        }
    }

    private static void bindRequest(PreparedStatement statement, TransactionRequest request)
            throws SQLException {
        statement.setObject(1, request.transactionId().value());
        statement.setString(2, request.idempotencyKey());
        setNullableUuid(statement, 3, request.characterId().map(CharacterId::value));
        setNullableUuid(statement, 4, request.sessionId().map(SessionId::value));
        statement.setString(5, request.operationType());
        statement.setString(6, request.reservedInputsJson());
        statement.setString(7, request.intendedOutputsJson());
        statement.setString(8, request.contentVersion());
    }

    private static void setNullableUuid(
            PreparedStatement statement, int index, Optional<java.util.UUID> value)
            throws SQLException {
        if (value.isPresent()) {
            statement.setObject(index, value.orElseThrow());
        } else {
            statement.setNull(index, Types.OTHER);
        }
    }

    private static Optional<TransactionJournalEntry> queryOptional(PreparedStatement statement)
            throws SQLException {
        try (ResultSet row = statement.executeQuery()) {
            return row.next() ? Optional.of(readEntry(row)) : Optional.empty();
        }
    }

    private static TransactionJournalEntry readEntry(ResultSet row) throws SQLException {
        java.util.UUID characterUuid = row.getObject("character_id", java.util.UUID.class);
        java.util.UUID sessionUuid = row.getObject("session_id", java.util.UUID.class);
        return new TransactionJournalEntry(
                new TransactionId(row.getObject("transaction_id", java.util.UUID.class)),
                row.getString("idempotency_key"),
                Optional.ofNullable(characterUuid).map(CharacterId::new),
                Optional.ofNullable(sessionUuid).map(SessionId::new),
                row.getString("operation_type"),
                TransactionState.valueOf(row.getString("state")),
                row.getString("reserved_inputs"),
                row.getString("intended_outputs"),
                row.getString("content_version"),
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private <T> Result<T, TransactionErrorCode> inTransaction(TransactionWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                Result<T, TransactionErrorCode> result = work.execute(connection);
                if (result.isSuccess()) {
                    connection.commit();
                } else {
                    connection.rollback();
                }
                return result;
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                return failure(exception);
            } finally {
                restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (SQLException exception) {
            return failure(exception);
        }
    }

    static <T> Result<T, TransactionErrorCode> failure(SQLException exception) {
        String sqlState = exception.getSQLState();
        TransactionErrorCode code =
                switch (sqlState == null ? "" : sqlState) {
                    case "22P02" -> TransactionErrorCode.TRANSACTION_INVALID_JSON;
                    case "23505" -> TransactionErrorCode.TRANSACTION_ID_CONFLICT;
                    default -> TransactionErrorCode.TRANSACTION_DATABASE_UNAVAILABLE;
                };
        return Result.failure(
                code,
                exception.getClass().getSimpleName()
                        + (sqlState == null ? "" : " SQLSTATE=" + sqlState));
    }

    static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original failure.
        }
    }

    static void restoreAutoCommit(Connection connection, boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
            // Closing the pooled connection is the only safe recovery left.
        }
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        Result<T, TransactionErrorCode> execute(Connection connection) throws SQLException;
    }

    private record ExistingRequest(TransactionJournalEntry entry, boolean matchesRequest) {}
}
