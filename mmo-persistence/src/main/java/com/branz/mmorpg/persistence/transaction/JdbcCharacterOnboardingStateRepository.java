package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.result.Result;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/** Journaled repository for immutable foundation choice and resumable starter-kit completion. */
public final class JdbcCharacterOnboardingStateRepository
        implements CharacterOnboardingStateRepository {
    public static final String FOUNDATION_CHOOSE = "character.onboarding.foundation.choose";
    public static final String KIT_READY = "character.onboarding.kit.ready";

    private static final String COLUMNS =
            """
            character_id, foundation_id, kit_ready, content_version, version,
            last_transaction_id, created_at, updated_at
            """;

    private final DataSource dataSource;

    public JdbcCharacterOnboardingStateRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Result<Optional<CharacterOnboardingStateRecord>, TransactionErrorCode> find(
            CharacterId characterId) {
        Objects.requireNonNull(characterId, "characterId");
        try (Connection connection = dataSource.getConnection()) {
            return Result.success(find(connection, characterId));
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    @Override
    public Result<CharacterOnboardingStateCommitExecution, TransactionErrorCode> chooseFoundation(
            TransactionRequest request, CharacterId characterId, String foundationId) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(characterId, "characterId");
        foundationId = requireFoundation(foundationId);
        Result<Boolean, TransactionErrorCode> requestCheck =
                validateRequest(request, characterId, FOUNDATION_CHOOSE);
        if (!requestCheck.isSuccess()) {
            Result.Failure<Boolean, TransactionErrorCode> failure =
                    (Result.Failure<Boolean, TransactionErrorCode>) requestCheck;
            return Result.failure(failure.error(), failure.detail());
        }
        final String chosenFoundation = foundationId;
        return execute(
                request,
                characterId,
                connection -> insertChoice(connection, request, characterId, chosenFoundation));
    }

    @Override
    public Result<CharacterOnboardingStateCommitExecution, TransactionErrorCode> markKitReady(
            TransactionRequest request, CharacterId characterId, long expectedVersion) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(characterId, "characterId");
        if (expectedVersion < 1) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Onboarding version must be positive.");
        }
        Result<Boolean, TransactionErrorCode> requestCheck =
                validateRequest(request, characterId, KIT_READY);
        if (!requestCheck.isSuccess()) {
            Result.Failure<Boolean, TransactionErrorCode> failure =
                    (Result.Failure<Boolean, TransactionErrorCode>) requestCheck;
            return Result.failure(failure.error(), failure.detail());
        }
        return execute(
                request,
                characterId,
                connection -> markReady(connection, request, characterId, expectedVersion));
    }

    private Result<CharacterOnboardingStateCommitExecution, TransactionErrorCode> execute(
            TransactionRequest request, CharacterId characterId, Mutation mutation) {
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
                                "Existing onboarding transaction is not committed.");
                    }
                    CharacterOnboardingStateRecord replayed =
                            find(connection, characterId)
                                    .orElseThrow(
                                            () ->
                                                    new SQLException(
                                                            "Committed onboarding state is missing."));
                    connection.commit();
                    return Result.success(
                            new CharacterOnboardingStateCommitExecution(
                                    replayed, new TransactionExecution(prepared.entry(), true)));
                }

                CharacterOnboardingStateRecord record = mutation.apply(connection);
                if (record == null) {
                    connection.rollback();
                    return Result.failure(
                            TransactionErrorCode.VALUE_STALE_VERSION,
                            "Character onboarding state changed before commit.");
                }
                appendAudit(connection, request, record);
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
                        new CharacterOnboardingStateCommitExecution(
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

    private static CharacterOnboardingStateRecord insertChoice(
            Connection connection,
            TransactionRequest request,
            CharacterId characterId,
            String foundationId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO character_onboarding_state(
                            character_id, foundation_id, kit_ready, content_version, version,
                            last_transaction_id, created_at, updated_at
                        ) VALUES (?, ?, FALSE, ?, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON CONFLICT (character_id) DO NOTHING
                        RETURNING
                        """
                                + COLUMNS)) {
            statement.setObject(1, characterId.value());
            statement.setString(2, foundationId);
            statement.setString(3, request.contentVersion());
            statement.setObject(4, request.transactionId().value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? read(row) : null;
            }
        }
    }

    private static CharacterOnboardingStateRecord markReady(
            Connection connection,
            TransactionRequest request,
            CharacterId characterId,
            long expectedVersion)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        UPDATE character_onboarding_state
                        SET kit_ready = TRUE,
                            content_version = ?,
                            version = version + 1,
                            last_transaction_id = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE character_id = ? AND version = ? AND kit_ready = FALSE
                        RETURNING
                        """
                                + COLUMNS)) {
            statement.setString(1, request.contentVersion());
            statement.setObject(2, request.transactionId().value());
            statement.setObject(3, characterId.value());
            statement.setLong(4, expectedVersion);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? read(row) : null;
            }
        }
    }

    private static Optional<CharacterOnboardingStateRecord> find(
            Connection connection, CharacterId characterId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT "
                                + COLUMNS
                                + " FROM character_onboarding_state WHERE character_id = ?")) {
            statement.setObject(1, characterId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row)) : Optional.empty();
            }
        }
    }

    private static CharacterOnboardingStateRecord read(ResultSet row) throws SQLException {
        return new CharacterOnboardingStateRecord(
                new CharacterId(row.getObject("character_id", java.util.UUID.class)),
                row.getString("foundation_id"),
                row.getBoolean("kit_ready"),
                row.getString("content_version"),
                row.getLong("version"),
                new com.branz.mmorpg.api.identity.TransactionId(
                        row.getObject("last_transaction_id", java.util.UUID.class)),
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static Result<Boolean, TransactionErrorCode> validateRequest(
            TransactionRequest request, CharacterId characterId, String operation) {
        if (!request.operationType().equals(operation)) {
            return Result.failure(
                    TransactionErrorCode.TRANSACTION_OPERATION_MISMATCH,
                    "Transaction operation does not match onboarding mutation.");
        }
        if (request.characterId().filter(characterId::equals).isEmpty()) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Transaction character does not match onboarding owner.");
        }
        return Result.success(Boolean.TRUE);
    }

    private static void appendAudit(
            Connection connection,
            TransactionRequest request,
            CharacterOnboardingStateRecord record)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO audit_log(
                            transaction_id, actor_character_id, action_type,
                            subject_type, subject_id, details, created_at
                        ) VALUES (?, ?, ?, 'CHARACTER', ?, CAST(? AS JSONB), CURRENT_TIMESTAMP)
                        """)) {
            statement.setObject(1, request.transactionId().value());
            statement.setObject(2, record.characterId().value());
            statement.setString(3, request.operationType());
            statement.setObject(4, record.characterId().value());
            statement.setString(
                    5,
                    "{\"foundation\":\""
                            + escapeJson(record.foundationId())
                            + "\",\"kitReady\":"
                            + record.kitReady()
                            + ",\"version\":"
                            + record.version()
                            + "}");
            statement.executeUpdate();
        }
    }

    private static String requireFoundation(String value) {
        Objects.requireNonNull(value, "foundationId");
        if (value.isBlank()) {
            throw new IllegalArgumentException("foundationId must not be blank");
        }
        return value;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @FunctionalInterface
    private interface Mutation {
        CharacterOnboardingStateRecord apply(Connection connection) throws SQLException;
    }
}
