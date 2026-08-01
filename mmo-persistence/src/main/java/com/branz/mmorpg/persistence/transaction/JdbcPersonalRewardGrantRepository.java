package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Journaled optimistic personal reward freeze/roll/delivery ledger. */
public final class JdbcPersonalRewardGrantRepository implements PersonalRewardGrantRepository {
    public static final String PERSONAL_REWARD_GRANT_COMMIT = "encounter.reward.grant.commit";
    private static final String COLUMNS =
            """
            grant_id, encounter_id, attempt, character_id, roll_seed, state, state_payload,
            content_version, version, last_transaction_id, created_at, updated_at
            """;

    private final DataSource dataSource;

    public JdbcPersonalRewardGrantRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Result<Optional<PersonalRewardGrantRecord>, TransactionErrorCode> find(UUID grantId) {
        Objects.requireNonNull(grantId, "grantId");
        try (Connection connection = dataSource.getConnection()) {
            return Result.success(find(connection, grantId));
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    @Override
    public Result<List<PersonalRewardGrantRecord>, TransactionErrorCode> findPending() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT "
                                        + COLUMNS
                                        + " FROM personal_reward_grant WHERE state <> 'DELIVERED'"
                                        + " ORDER BY updated_at, grant_id");
                ResultSet rows = statement.executeQuery()) {
            ArrayList<PersonalRewardGrantRecord> records = new ArrayList<>();
            while (rows.next()) {
                records.add(read(rows));
            }
            return Result.success(List.copyOf(records));
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    @Override
    public Result<PersonalRewardGrantCommitExecution, TransactionErrorCode> commit(
            TransactionRequest request, PersonalRewardGrantCommit commit) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(commit, "commit");
        if (!request.operationType().equals(PERSONAL_REWARD_GRANT_COMMIT)) {
            return Result.failure(
                    TransactionErrorCode.TRANSACTION_OPERATION_MISMATCH,
                    "Transaction operation does not match a personal reward grant commit.");
        }
        if (request.characterId().isPresent() || request.sessionId().isPresent()) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Personal reward grant commits must use a system transaction.");
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
                                "Existing personal reward transaction is not committed.");
                    }
                    PersonalRewardGrantRecord replayed =
                            find(connection, commit.grantId())
                                    .orElseThrow(
                                            () ->
                                                    new SQLException(
                                                            "Committed personal reward is missing."));
                    connection.commit();
                    return Result.success(
                            new PersonalRewardGrantCommitExecution(
                                    replayed, new TransactionExecution(prepared.entry(), true)));
                }

                PersonalRewardGrantRecord record = mutate(connection, request, commit);
                if (record == null) {
                    connection.rollback();
                    return Result.failure(
                            TransactionErrorCode.VALUE_STALE_VERSION,
                            "Personal reward grant changed or transition is invalid.");
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
                        new PersonalRewardGrantCommitExecution(
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

    private static PersonalRewardGrantRecord mutate(
            Connection connection, TransactionRequest request, PersonalRewardGrantCommit commit)
            throws SQLException {
        String sql =
                commit.expectedVersion() == 0
                        ? """
                        INSERT INTO personal_reward_grant(
                            grant_id, encounter_id, attempt, character_id, roll_seed, state,
                            state_payload, content_version, version, last_transaction_id,
                            created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, CAST(? AS personal_reward_grant_state),
                            CAST(? AS JSONB), ?, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON CONFLICT DO NOTHING
                        RETURNING
                        """
                                + COLUMNS
                        : """
                        UPDATE personal_reward_grant
                        SET state = CAST(? AS personal_reward_grant_state),
                            state_payload = CAST(? AS JSONB), content_version = ?,
                            version = version + 1, last_transaction_id = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE grant_id = ? AND encounter_id = ? AND attempt = ?
                            AND character_id = ? AND roll_seed = ? AND version = ?
                            AND ((state = 'FROZEN' AND ? IN ('FROZEN', 'ROLLED'))
                                OR (state = 'ROLLED' AND ? IN ('ROLLED', 'DELIVERED'))
                                OR (state = 'DELIVERED' AND ? = 'DELIVERED'))
                        RETURNING
                        """
                                + COLUMNS;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (commit.expectedVersion() == 0) {
                statement.setObject(1, commit.grantId());
                statement.setObject(2, commit.encounterId().value());
                statement.setInt(3, commit.attempt());
                statement.setObject(4, commit.characterId().value());
                statement.setLong(5, commit.rollSeed());
                statement.setString(6, commit.state().name());
                statement.setString(7, commit.replacementPayloadJson());
                statement.setString(8, request.contentVersion());
                statement.setObject(9, request.transactionId().value());
            } else {
                statement.setString(1, commit.state().name());
                statement.setString(2, commit.replacementPayloadJson());
                statement.setString(3, request.contentVersion());
                statement.setObject(4, request.transactionId().value());
                statement.setObject(5, commit.grantId());
                statement.setObject(6, commit.encounterId().value());
                statement.setInt(7, commit.attempt());
                statement.setObject(8, commit.characterId().value());
                statement.setLong(9, commit.rollSeed());
                statement.setLong(10, commit.expectedVersion());
                statement.setString(11, commit.state().name());
                statement.setString(12, commit.state().name());
                statement.setString(13, commit.state().name());
            }
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? read(row) : null;
            }
        }
    }

    private static Optional<PersonalRewardGrantRecord> find(Connection connection, UUID grantId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT " + COLUMNS + " FROM personal_reward_grant WHERE grant_id = ?")) {
            statement.setObject(1, grantId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row)) : Optional.empty();
            }
        }
    }

    private static PersonalRewardGrantRecord read(ResultSet row) throws SQLException {
        return new PersonalRewardGrantRecord(
                row.getObject("grant_id", UUID.class),
                new EncounterId(row.getObject("encounter_id", UUID.class)),
                row.getInt("attempt"),
                new CharacterId(row.getObject("character_id", UUID.class)),
                row.getLong("roll_seed"),
                PersonalRewardGrantState.valueOf(row.getString("state")),
                row.getString("state_payload"),
                row.getString("content_version"),
                row.getLong("version"),
                new TransactionId(row.getObject("last_transaction_id", UUID.class)),
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static void appendAudit(
            Connection connection, TransactionRequest request, PersonalRewardGrantRecord record)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO audit_log(
                            transaction_id, actor_character_id, action_type,
                            subject_type, subject_id, details, created_at
                        ) VALUES (?, NULL, ?, 'REWARD_GRANT', ?, CAST(? AS JSONB), CURRENT_TIMESTAMP)
                        """)) {
            statement.setObject(1, request.transactionId().value());
            statement.setString(2, PERSONAL_REWARD_GRANT_COMMIT);
            statement.setObject(3, record.grantId());
            statement.setString(
                    4,
                    "{\"version\":" + record.version() + ",\"state\":\"" + record.state() + "\"}");
            statement.executeUpdate();
        }
    }
}
