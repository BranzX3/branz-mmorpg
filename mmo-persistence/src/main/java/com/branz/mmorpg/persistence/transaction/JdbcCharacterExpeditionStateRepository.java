package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/** Journaled optimistic repository for Flask, consumable-effect and ailment state. */
public final class JdbcCharacterExpeditionStateRepository
        implements CharacterExpeditionStateRepository {
    public static final String CHARACTER_EXPEDITION_STATE_COMMIT =
            "character.expedition-state.commit";
    public static final String CHARACTER_FLASK_PREPARATION_COMMIT =
            "character.flask-preparation.commit";

    private static final String COLUMNS =
            "character_id, state_payload, content_version, version, "
                    + "last_transaction_id, created_at, updated_at";

    private final DataSource dataSource;

    public JdbcCharacterExpeditionStateRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Result<Optional<CharacterExpeditionStateRecord>, TransactionErrorCode> find(
            CharacterId characterId) {
        Objects.requireNonNull(characterId, "characterId");
        try (Connection connection = dataSource.getConnection()) {
            return Result.success(find(connection, characterId));
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        } catch (IllegalArgumentException exception) {
            return invalidState();
        }
    }

    @Override
    public Result<CharacterExpeditionStateCommitExecution, TransactionErrorCode> commit(
            TransactionRequest request, CharacterExpeditionStateCommit commit) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(commit, "commit");
        if (!request.operationType().equals(CHARACTER_EXPEDITION_STATE_COMMIT)) {
            return Result.failure(
                    TransactionErrorCode.TRANSACTION_OPERATION_MISMATCH,
                    "Transaction operation does not match an expedition-state commit.");
        }
        if (request.characterId().filter(commit.characterId()::equals).isEmpty()) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Transaction character does not match the expedition-state owner.");
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
                                "Existing expedition-state transaction is not committed.");
                    }
                    CharacterExpeditionStateRecord replayed =
                            find(connection, commit.characterId())
                                    .orElseThrow(
                                            () ->
                                                    new SQLException(
                                                            "Committed expedition state is missing."));
                    connection.commit();
                    return Result.success(
                            new CharacterExpeditionStateCommitExecution(
                                    replayed, new TransactionExecution(prepared.entry(), true)));
                }

                CharacterExpeditionStateRecord record = mutate(connection, request, commit);
                if (record == null) {
                    connection.rollback();
                    return Result.failure(
                            TransactionErrorCode.VALUE_STALE_VERSION,
                            "Character expedition state changed before commit.");
                }
                appendAudit(connection, request, record, CHARACTER_EXPEDITION_STATE_COMMIT, 0);
                Result<JournalTransitionOutcome, TransactionErrorCode> transition =
                        JdbcTransactionJournalRepository.transition(
                                connection, request.transactionId(), TransactionState.COMMITTED);
                if (transition
                        instanceof
                        Result.Failure<JournalTransitionOutcome, TransactionErrorCode> failure) {
                    connection.rollback();
                    return Result.failure(failure.error(), failure.detail());
                }
                TransactionJournalEntry journal =
                        ((Result.Success<JournalTransitionOutcome, TransactionErrorCode>)
                                        transition)
                                .value()
                                .entry();
                connection.commit();
                return Result.success(
                        new CharacterExpeditionStateCommitExecution(
                                record, new TransactionExecution(journal, false)));
            } catch (SQLException exception) {
                JdbcTransactionJournalRepository.rollbackQuietly(connection);
                return JdbcTransactionJournalRepository.failure(exception);
            } catch (IllegalArgumentException exception) {
                JdbcTransactionJournalRepository.rollbackQuietly(connection);
                return invalidState();
            } finally {
                JdbcTransactionJournalRepository.restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    @Override
    public Result<CharacterFlaskPreparationCommitExecution, TransactionErrorCode>
            commitFlaskPreparation(
                    TransactionRequest request, CharacterFlaskPreparationCommit commit) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(commit, "commit");
        if (!request.operationType().equals(CHARACTER_FLASK_PREPARATION_COMMIT)) {
            return Result.failure(
                    TransactionErrorCode.TRANSACTION_OPERATION_MISMATCH,
                    "Transaction operation does not match a Flask preparation commit.");
        }
        if (request.characterId().filter(commit.characterId()::equals).isEmpty()) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Transaction character does not match the Flask owner.");
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
                                "Existing Flask preparation transaction is not committed.");
                    }
                    CharacterExpeditionStateRecord replayed =
                            find(connection, commit.characterId())
                                    .orElseThrow(
                                            () ->
                                                    new SQLException(
                                                            "Committed Flask state is missing."));
                    connection.commit();
                    return Result.success(
                            new CharacterFlaskPreparationCommitExecution(
                                    replayed,
                                    commit.totalStockConsumed(),
                                    new TransactionExecution(prepared.entry(), true)));
                }

                for (LotQuantityConsumption consumption : commit.stockConsumptions()) {
                    Optional<LotLocationRecord> current =
                            JdbcValueTransactionService.findLot(connection, consumption.lotId());
                    if (current.isEmpty()) {
                        connection.rollback();
                        return Result.failure(
                                TransactionErrorCode.VALUE_NOT_FOUND,
                                "Infusion Stock lot does not exist.");
                    }
                    if (!current.orElseThrow()
                            .definitionId()
                            .equals(commit.infusionStockDefinitionId())) {
                        connection.rollback();
                        return Result.failure(
                                TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                                "Rest preparation may consume only Infusion Stock.");
                    }
                    Result<JdbcValueTransactionService.MutationApplied, TransactionErrorCode>
                            consumed =
                                    JdbcValueTransactionService.updateLotQuantity(
                                            connection, request.transactionId(), consumption);
                    if (consumed
                            instanceof
                            Result.Failure<
                                            JdbcValueTransactionService.MutationApplied,
                                            TransactionErrorCode>
                                    failure) {
                        connection.rollback();
                        return Result.failure(failure.error(), failure.detail());
                    }
                }

                CharacterExpeditionStateRecord record =
                        mutate(
                                connection,
                                request,
                                new CharacterExpeditionStateCommit(
                                        commit.characterId(),
                                        commit.expectedStateVersion(),
                                        commit.replacementPayloadJson()));
                if (record == null) {
                    connection.rollback();
                    return Result.failure(
                            TransactionErrorCode.VALUE_STALE_VERSION,
                            "Character expedition state changed before Rest commit.");
                }
                appendAudit(
                        connection,
                        request,
                        record,
                        CHARACTER_FLASK_PREPARATION_COMMIT,
                        commit.totalStockConsumed());
                Result<JournalTransitionOutcome, TransactionErrorCode> transition =
                        JdbcTransactionJournalRepository.transition(
                                connection, request.transactionId(), TransactionState.COMMITTED);
                if (transition
                        instanceof
                        Result.Failure<JournalTransitionOutcome, TransactionErrorCode> failure) {
                    connection.rollback();
                    return Result.failure(failure.error(), failure.detail());
                }
                TransactionJournalEntry journal =
                        ((Result.Success<JournalTransitionOutcome, TransactionErrorCode>)
                                        transition)
                                .value()
                                .entry();
                connection.commit();
                return Result.success(
                        new CharacterFlaskPreparationCommitExecution(
                                record,
                                commit.totalStockConsumed(),
                                new TransactionExecution(journal, false)));
            } catch (SQLException exception) {
                JdbcTransactionJournalRepository.rollbackQuietly(connection);
                return JdbcTransactionJournalRepository.failure(exception);
            } catch (IllegalArgumentException exception) {
                JdbcTransactionJournalRepository.rollbackQuietly(connection);
                return invalidState();
            } finally {
                JdbcTransactionJournalRepository.restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    private static CharacterExpeditionStateRecord mutate(
            Connection connection,
            TransactionRequest request,
            CharacterExpeditionStateCommit commit)
            throws SQLException {
        String sql =
                commit.expectedVersion() == 0
                        ? "INSERT INTO character_expedition_state("
                                + COLUMNS
                                + ") VALUES (?, CAST(? AS JSONB), ?, 1, ?, CURRENT_TIMESTAMP, "
                                + "CURRENT_TIMESTAMP) ON CONFLICT (character_id) DO NOTHING "
                                + "RETURNING "
                                + COLUMNS
                        : "UPDATE character_expedition_state SET state_payload = CAST(? AS JSONB), "
                                + "content_version = ?, version = version + 1, "
                                + "last_transaction_id = ?, updated_at = CURRENT_TIMESTAMP "
                                + "WHERE character_id = ? AND version = ? RETURNING "
                                + COLUMNS;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (commit.expectedVersion() == 0) {
                statement.setObject(1, commit.characterId().value());
                statement.setString(2, commit.replacementPayloadJson());
                statement.setString(3, request.contentVersion());
                statement.setObject(4, request.transactionId().value());
            } else {
                statement.setString(1, commit.replacementPayloadJson());
                statement.setString(2, request.contentVersion());
                statement.setObject(3, request.transactionId().value());
                statement.setObject(4, commit.characterId().value());
                statement.setLong(5, commit.expectedVersion());
            }
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? read(row) : null;
            }
        }
    }

    private static Optional<CharacterExpeditionStateRecord> find(
            Connection connection, CharacterId characterId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT "
                                + COLUMNS
                                + " FROM character_expedition_state WHERE character_id = ?")) {
            statement.setObject(1, characterId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row)) : Optional.empty();
            }
        }
    }

    private static CharacterExpeditionStateRecord read(ResultSet row) throws SQLException {
        return new CharacterExpeditionStateRecord(
                new CharacterId(row.getObject("character_id", java.util.UUID.class)),
                row.getString("state_payload"),
                row.getString("content_version"),
                row.getLong("version"),
                new TransactionId(row.getObject("last_transaction_id", java.util.UUID.class)),
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static void appendAudit(
            Connection connection,
            TransactionRequest request,
            CharacterExpeditionStateRecord record,
            String actionType,
            long infusionStockConsumed)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "INSERT INTO audit_log(transaction_id, actor_character_id, action_type, "
                                + "subject_type, subject_id, details, created_at) VALUES (?, ?, ?, "
                                + "'CHARACTER', ?, CAST(? AS JSONB), CURRENT_TIMESTAMP)")) {
            statement.setObject(1, request.transactionId().value());
            statement.setObject(2, record.characterId().value());
            statement.setString(3, actionType);
            statement.setObject(4, record.characterId().value());
            statement.setString(
                    5,
                    "{\"version\":"
                            + record.version()
                            + ",\"infusionStockConsumed\":"
                            + infusionStockConsumed
                            + ",\"contentVersion\":\""
                            + escapeJson(record.contentVersion())
                            + "\"}");
            statement.executeUpdate();
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static <T> Result<T, TransactionErrorCode> invalidState() {
        return Result.failure(
                TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                "Persisted character expedition state is invalid.");
    }
}
