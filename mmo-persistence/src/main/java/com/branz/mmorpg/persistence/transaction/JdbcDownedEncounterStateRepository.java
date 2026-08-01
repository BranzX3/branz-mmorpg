package com.branz.mmorpg.persistence.transaction;

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
import javax.sql.DataSource;

/** Journaled optimistic repository for restart-safe party downed state. */
public final class JdbcDownedEncounterStateRepository implements DownedEncounterStateRepository {
    public static final String DOWNED_ENCOUNTER_STATE_COMMIT = "encounter.downed.state.commit";

    private static final String COLUMNS =
            """
            encounter_id, attempt, recoverable, state_payload, content_version, version,
            last_transaction_id, created_at, updated_at
            """;

    private final DataSource dataSource;

    public JdbcDownedEncounterStateRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Result<Optional<DownedEncounterStateRecord>, TransactionErrorCode> find(
            EncounterId encounterId) {
        Objects.requireNonNull(encounterId, "encounterId");
        try (Connection connection = dataSource.getConnection()) {
            return Result.success(find(connection, encounterId));
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    @Override
    public Result<List<DownedEncounterStateRecord>, TransactionErrorCode> findRecoverable() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT "
                                        + COLUMNS
                                        + " FROM downed_encounter_state WHERE recoverable"
                                        + " ORDER BY updated_at, encounter_id");
                ResultSet rows = statement.executeQuery()) {
            ArrayList<DownedEncounterStateRecord> records = new ArrayList<>();
            while (rows.next()) {
                records.add(read(rows));
            }
            return Result.success(List.copyOf(records));
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    @Override
    public Result<DownedEncounterStateCommitExecution, TransactionErrorCode> commit(
            TransactionRequest request, DownedEncounterStateCommit commit) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(commit, "commit");
        if (!request.operationType().equals(DOWNED_ENCOUNTER_STATE_COMMIT)) {
            return Result.failure(
                    TransactionErrorCode.TRANSACTION_OPERATION_MISMATCH,
                    "Transaction operation does not match a downed encounter state commit.");
        }
        if (request.characterId().isPresent() || request.sessionId().isPresent()) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Downed encounter state commits must use a system transaction.");
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
                                "Existing downed-state transaction is not committed.");
                    }
                    DownedEncounterStateRecord replayed =
                            find(connection, commit.encounterId())
                                    .orElseThrow(
                                            () ->
                                                    new SQLException(
                                                            "Committed downed state is missing."));
                    connection.commit();
                    return Result.success(
                            new DownedEncounterStateCommitExecution(
                                    replayed, new TransactionExecution(prepared.entry(), true)));
                }

                DownedEncounterStateRecord record = mutate(connection, request, commit);
                if (record == null) {
                    connection.rollback();
                    return Result.failure(
                            TransactionErrorCode.VALUE_STALE_VERSION,
                            "Downed encounter state changed before commit.");
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
                        new DownedEncounterStateCommitExecution(
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

    private static DownedEncounterStateRecord mutate(
            Connection connection, TransactionRequest request, DownedEncounterStateCommit commit)
            throws SQLException {
        String sql =
                commit.expectedVersion() == 0
                        ? """
                        INSERT INTO downed_encounter_state(
                            encounter_id, attempt, recoverable, state_payload, content_version,
                            version, last_transaction_id, created_at, updated_at
                        ) VALUES (?, ?, ?, CAST(? AS JSONB), ?, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON CONFLICT (encounter_id) DO NOTHING
                        RETURNING
                        """
                                + COLUMNS
                        : """
                        UPDATE downed_encounter_state
                        SET attempt = ?, recoverable = ?, state_payload = CAST(? AS JSONB),
                            content_version = ?, version = version + 1,
                            last_transaction_id = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE encounter_id = ? AND version = ?
                        RETURNING
                        """
                                + COLUMNS;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (commit.expectedVersion() == 0) {
                statement.setObject(1, commit.encounterId().value());
                statement.setInt(2, commit.attempt());
                statement.setBoolean(3, commit.recoverable());
                statement.setString(4, commit.replacementPayloadJson());
                statement.setString(5, request.contentVersion());
                statement.setObject(6, request.transactionId().value());
            } else {
                statement.setInt(1, commit.attempt());
                statement.setBoolean(2, commit.recoverable());
                statement.setString(3, commit.replacementPayloadJson());
                statement.setString(4, request.contentVersion());
                statement.setObject(5, request.transactionId().value());
                statement.setObject(6, commit.encounterId().value());
                statement.setLong(7, commit.expectedVersion());
            }
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? read(row) : null;
            }
        }
    }

    private static Optional<DownedEncounterStateRecord> find(
            Connection connection, EncounterId encounterId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT "
                                + COLUMNS
                                + " FROM downed_encounter_state WHERE encounter_id = ?")) {
            statement.setObject(1, encounterId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row)) : Optional.empty();
            }
        }
    }

    private static DownedEncounterStateRecord read(ResultSet row) throws SQLException {
        return new DownedEncounterStateRecord(
                new EncounterId(row.getObject("encounter_id", java.util.UUID.class)),
                row.getInt("attempt"),
                row.getBoolean("recoverable"),
                row.getString("state_payload"),
                row.getString("content_version"),
                row.getLong("version"),
                new TransactionId(row.getObject("last_transaction_id", java.util.UUID.class)),
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static void appendAudit(
            Connection connection, TransactionRequest request, DownedEncounterStateRecord record)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO audit_log(
                            transaction_id, actor_character_id, action_type,
                            subject_type, subject_id, details, created_at
                        ) VALUES (?, NULL, ?, 'DOWNED_ENCOUNTER', ?, CAST(? AS JSONB), CURRENT_TIMESTAMP)
                        """)) {
            statement.setObject(1, request.transactionId().value());
            statement.setString(2, DOWNED_ENCOUNTER_STATE_COMMIT);
            statement.setObject(3, record.encounterId().value());
            statement.setString(
                    4,
                    "{\"version\":"
                            + record.version()
                            + ",\"attempt\":"
                            + record.attempt()
                            + ",\"recoverable\":"
                            + record.recoverable()
                            + "}");
            statement.executeUpdate();
        }
    }
}
