package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Journaled optimistic Death Pouch saga ledger. */
public final class JdbcDeathPouchRepository implements DeathPouchRepository {
    public static final String DEATH_POUCH_COMMIT = "death.pouch.commit";
    private static final String COLUMNS =
            """
            pouch_id, death_id, owner_character_id, amount, wallet_debit_operation_id,
            wallet_credit_operation_id, world_key, location_x, location_y, location_z,
            state, state_payload, content_version, version, last_transaction_id,
            created_at, expires_at, updated_at
            """;

    private final DataSource dataSource;

    public JdbcDeathPouchRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Result<Optional<DeathPouchRecord>, TransactionErrorCode> find(UUID pouchId) {
        Objects.requireNonNull(pouchId, "pouchId");
        try (Connection connection = dataSource.getConnection()) {
            return Result.success(find(connection, pouchId));
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    @Override
    public Result<List<DeathPouchRecord>, TransactionErrorCode> findActive(
            CharacterId ownerCharacterId) {
        Objects.requireNonNull(ownerCharacterId, "ownerCharacterId");
        return query(
                "SELECT "
                        + COLUMNS
                        + " FROM death_pouch WHERE owner_character_id = ? AND state = 'ACTIVE'"
                        + " ORDER BY created_at, pouch_id",
                statement -> statement.setObject(1, ownerCharacterId.value()));
    }

    @Override
    public Result<List<DeathPouchRecord>, TransactionErrorCode> findRecoverable() {
        return query(
                "SELECT "
                        + COLUMNS
                        + " FROM death_pouch WHERE state IN ('PENDING_DEBIT', 'RECOVERING')"
                        + " ORDER BY updated_at, pouch_id",
                statement -> {});
    }

    @Override
    public Result<List<DeathPouchRecord>, TransactionErrorCode> findExpirable(Instant now) {
        Objects.requireNonNull(now, "now");
        return query(
                "SELECT "
                        + COLUMNS
                        + " FROM death_pouch WHERE state IN ('PENDING_DEBIT', 'ACTIVE')"
                        + " AND expires_at <= ? ORDER BY expires_at, pouch_id",
                statement -> statement.setObject(1, offset(now)));
    }

    @Override
    public Result<DeathPouchCommitExecution, TransactionErrorCode> commit(
            TransactionRequest request, DeathPouchCommit commit) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(commit, "commit");
        if (!request.operationType().equals(DEATH_POUCH_COMMIT)) {
            return Result.failure(
                    TransactionErrorCode.TRANSACTION_OPERATION_MISMATCH,
                    "Transaction operation does not match a Death Pouch commit.");
        }
        if (request.characterId().isPresent() || request.sessionId().isPresent()) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Death Pouch commits must use a system transaction.");
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                Result<JournalPrepareOutcome, TransactionErrorCode> preparedResult =
                        JdbcTransactionJournalRepository.prepare(connection, request);
                if (preparedResult
                        instanceof
                        Result.Failure<JournalPrepareOutcome, TransactionErrorCode> failure) {
                    connection.rollback();
                    return Result.failure(failure.error(), failure.detail());
                }
                JournalPrepareOutcome prepared =
                        ((Result.Success<JournalPrepareOutcome, TransactionErrorCode>)
                                        preparedResult)
                                .value();
                if (!prepared.newlyPrepared()) {
                    if (prepared.entry().state() != TransactionState.COMMITTED) {
                        connection.rollback();
                        return Result.failure(
                                TransactionErrorCode.TRANSACTION_INVALID_STATE,
                                "Existing Death Pouch transaction is not committed.");
                    }
                    DeathPouchRecord replayed =
                            find(connection, commit.pouchId())
                                    .orElseThrow(
                                            () ->
                                                    new SQLException(
                                                            "Committed Death Pouch is missing."));
                    connection.commit();
                    return Result.success(
                            new DeathPouchCommitExecution(
                                    replayed, new TransactionExecution(prepared.entry(), true)));
                }
                DeathPouchRecord record = mutate(connection, request, commit);
                if (record == null) {
                    connection.rollback();
                    return Result.failure(
                            TransactionErrorCode.VALUE_STALE_VERSION,
                            "Death Pouch changed or its state transition is invalid.");
                }
                appendAudit(connection, request, record);
                Result<JournalTransitionOutcome, TransactionErrorCode> transitioned =
                        JdbcTransactionJournalRepository.transition(
                                connection, request.transactionId(), TransactionState.COMMITTED);
                if (transitioned
                        instanceof
                        Result.Failure<JournalTransitionOutcome, TransactionErrorCode> failure) {
                    connection.rollback();
                    return Result.failure(failure.error(), failure.detail());
                }
                TransactionJournalEntry journal =
                        ((Result.Success<JournalTransitionOutcome, TransactionErrorCode>)
                                        transitioned)
                                .value()
                                .entry();
                connection.commit();
                return Result.success(
                        new DeathPouchCommitExecution(
                                record, new TransactionExecution(journal, false)));
            } catch (SQLException exception) {
                JdbcTransactionJournalRepository.rollbackQuietly(connection);
                return JdbcTransactionJournalRepository.failure(exception);
            } finally {
                JdbcTransactionJournalRepository.restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    private static DeathPouchRecord mutate(
            Connection connection, TransactionRequest request, DeathPouchCommit commit)
            throws SQLException {
        String sql =
                commit.expectedVersion() == 0
                        ? """
                        INSERT INTO death_pouch(
                            pouch_id, death_id, owner_character_id, amount,
                            wallet_debit_operation_id, wallet_credit_operation_id, world_key,
                            location_x, location_y, location_z, state, state_payload,
                            content_version, version, last_transaction_id, created_at,
                            expires_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS death_pouch_state),
                            CAST(? AS JSONB), ?, 1, ?, ?, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT DO NOTHING RETURNING
                        """
                                + COLUMNS
                        : """
                        UPDATE death_pouch SET state = CAST(? AS death_pouch_state),
                            state_payload = CAST(? AS JSONB), content_version = ?,
                            version = version + 1, last_transaction_id = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE pouch_id = ? AND death_id = ? AND owner_character_id = ?
                            AND amount = ? AND wallet_debit_operation_id = ?
                            AND wallet_credit_operation_id = ? AND world_key = ?
                            AND location_x = ? AND location_y = ? AND location_z = ?
                            AND created_at = ? AND expires_at = ? AND version = ?
                            AND ((state = 'PENDING_DEBIT' AND ? IN ('PENDING_DEBIT','ACTIVE','EXPIRED'))
                                OR (state = 'ACTIVE' AND ? IN ('ACTIVE','RECOVERING','EXPIRED'))
                                OR (state = 'RECOVERING' AND ? IN ('RECOVERING','RECOVERED'))
                                OR (state = 'RECOVERED' AND ? = 'RECOVERED')
                                OR (state = 'EXPIRED' AND ? = 'EXPIRED'))
                        RETURNING
                        """
                                + COLUMNS;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (commit.expectedVersion() == 0) {
                statement.setObject(1, commit.pouchId());
                statement.setObject(2, commit.deathId());
                statement.setObject(3, commit.ownerCharacterId().value());
                statement.setLong(4, commit.amount());
                statement.setObject(5, commit.walletDebitOperationId());
                statement.setObject(6, commit.walletCreditOperationId());
                statement.setString(7, commit.worldKey());
                statement.setDouble(8, commit.locationX());
                statement.setDouble(9, commit.locationY());
                statement.setDouble(10, commit.locationZ());
                statement.setString(11, commit.state().name());
                statement.setString(12, commit.replacementPayloadJson());
                statement.setString(13, request.contentVersion());
                statement.setObject(14, request.transactionId().value());
                statement.setObject(15, offset(commit.createdAt()));
                statement.setObject(16, offset(commit.expiresAt()));
            } else {
                statement.setString(1, commit.state().name());
                statement.setString(2, commit.replacementPayloadJson());
                statement.setString(3, request.contentVersion());
                statement.setObject(4, request.transactionId().value());
                statement.setObject(5, commit.pouchId());
                statement.setObject(6, commit.deathId());
                statement.setObject(7, commit.ownerCharacterId().value());
                statement.setLong(8, commit.amount());
                statement.setObject(9, commit.walletDebitOperationId());
                statement.setObject(10, commit.walletCreditOperationId());
                statement.setString(11, commit.worldKey());
                statement.setDouble(12, commit.locationX());
                statement.setDouble(13, commit.locationY());
                statement.setDouble(14, commit.locationZ());
                statement.setObject(15, offset(commit.createdAt()));
                statement.setObject(16, offset(commit.expiresAt()));
                statement.setLong(17, commit.expectedVersion());
                for (int index = 18; index <= 22; index++) {
                    statement.setString(index, commit.state().name());
                }
            }
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? read(row) : null;
            }
        }
    }

    private Result<List<DeathPouchRecord>, TransactionErrorCode> query(
            String sql, StatementBinder binder) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                ArrayList<DeathPouchRecord> records = new ArrayList<>();
                while (rows.next()) {
                    records.add(read(rows));
                }
                return Result.success(List.copyOf(records));
            }
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    private static Optional<DeathPouchRecord> find(Connection connection, UUID pouchId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT " + COLUMNS + " FROM death_pouch WHERE pouch_id = ?")) {
            statement.setObject(1, pouchId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row)) : Optional.empty();
            }
        }
    }

    private static DeathPouchRecord read(ResultSet row) throws SQLException {
        return new DeathPouchRecord(
                row.getObject("pouch_id", UUID.class),
                row.getObject("death_id", UUID.class),
                new CharacterId(row.getObject("owner_character_id", UUID.class)),
                row.getLong("amount"),
                row.getObject("wallet_debit_operation_id", UUID.class),
                row.getObject("wallet_credit_operation_id", UUID.class),
                row.getString("world_key"),
                row.getDouble("location_x"),
                row.getDouble("location_y"),
                row.getDouble("location_z"),
                DeathPouchState.valueOf(row.getString("state")),
                row.getString("state_payload"),
                row.getString("content_version"),
                row.getLong("version"),
                new TransactionId(row.getObject("last_transaction_id", UUID.class)),
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getObject("expires_at", OffsetDateTime.class).toInstant(),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static void appendAudit(
            Connection connection, TransactionRequest request, DeathPouchRecord record)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO audit_log(
                            transaction_id, actor_character_id, action_type,
                            subject_type, subject_id, details, created_at
                        ) VALUES (?, NULL, ?, 'DEATH_POUCH', ?, CAST(? AS JSONB), CURRENT_TIMESTAMP)
                        """)) {
            statement.setObject(1, request.transactionId().value());
            statement.setString(2, DEATH_POUCH_COMMIT);
            statement.setObject(3, record.pouchId());
            statement.setString(
                    4,
                    "{\"state\":\""
                            + record.state()
                            + "\",\"version\":"
                            + record.version()
                            + ",\"ownerCharacterId\":\""
                            + record.ownerCharacterId().value()
                            + "\"}");
            statement.executeUpdate();
        }
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
