package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.lifeskills.node.ResourceNodeId;
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

/** Atomic optimistic repository for node, tool, Lifeskill state and harvest output. */
public final class JdbcResourceNodeStateRepository implements ResourceNodeStateRepository {
    public static final String RESOURCE_NODE_STATE_COMMIT = "resource-node.state.commit";

    private static final String NODE_COLUMNS =
            "node_id, definition_id, phase, state_payload, content_version, version, "
                    + "last_transaction_id, created_at, updated_at";
    private static final String CHARACTER_COLUMNS =
            "character_id, state_payload, content_version, version, "
                    + "last_transaction_id, created_at, updated_at";

    private final DataSource dataSource;
    private final CheckpointObserver checkpointObserver;

    public JdbcResourceNodeStateRepository(DataSource dataSource) {
        this(dataSource, checkpoint -> {});
    }

    JdbcResourceNodeStateRepository(DataSource dataSource, CheckpointObserver checkpointObserver) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.checkpointObserver = Objects.requireNonNull(checkpointObserver, "checkpointObserver");
    }

    @Override
    public Result<Optional<ResourceNodeStateRecord>, TransactionErrorCode> find(
            ResourceNodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId");
        try (Connection connection = dataSource.getConnection()) {
            return Result.success(findNode(connection, nodeId));
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    @Override
    public Result<List<ResourceNodeStateRecord>, TransactionErrorCode> findRecoverable() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT "
                                        + NODE_COLUMNS
                                        + " FROM resource_node_state WHERE phase <> 'AVAILABLE'"
                                        + " ORDER BY updated_at, node_id");
                ResultSet rows = statement.executeQuery()) {
            ArrayList<ResourceNodeStateRecord> records = new ArrayList<>();
            while (rows.next()) {
                records.add(readNode(rows));
            }
            return Result.success(List.copyOf(records));
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    @Override
    public Result<Optional<CharacterLifeskillStateRecord>, TransactionErrorCode> findCharacterState(
            CharacterId characterId) {
        Objects.requireNonNull(characterId, "characterId");
        try (Connection connection = dataSource.getConnection()) {
            return Result.success(findCharacter(connection, characterId));
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    @Override
    public Result<ResourceNodeStateCommitExecution, TransactionErrorCode> commit(
            TransactionRequest request, ResourceNodeStateCommit commit) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(commit, "commit");
        Result<ResourceNodeStateCommitExecution, TransactionErrorCode> requestFailure =
                validateRequest(request, commit);
        if (requestFailure != null) {
            return requestFailure;
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
                                "Existing resource-node transaction is not committed.");
                    }
                    ResourceNodeStateCommitExecution replayed =
                            execution(connection, commit, prepared.entry(), true);
                    connection.commit();
                    return Result.success(replayed);
                }

                ResourceNodeStateRecord node = mutateNode(connection, request, commit);
                if (node == null) {
                    connection.rollback();
                    return Result.failure(
                            TransactionErrorCode.VALUE_STALE_VERSION,
                            "Resource-node state changed before commit.");
                }
                checkpointObserver.reached(Checkpoint.AFTER_NODE_MUTATION);
                Optional<CharacterLifeskillStateRecord> character = Optional.empty();
                if (commit.characterState().isPresent()) {
                    CharacterLifeskillStateRecord mutated =
                            mutateCharacter(
                                    connection, request, commit.characterState().orElseThrow());
                    if (mutated == null) {
                        connection.rollback();
                        return Result.failure(
                                TransactionErrorCode.VALUE_STALE_VERSION,
                                "Character Lifeskill state changed before harvest commit.");
                    }
                    character = Optional.of(mutated);
                    checkpointObserver.reached(Checkpoint.AFTER_CHARACTER_MUTATION);
                }
                Optional<ItemLocationRecord> tool = Optional.empty();
                if (commit.toolUpdate().isPresent()) {
                    ItemPayloadUpdate update = commit.toolUpdate().orElseThrow();
                    Result<JdbcValueTransactionService.MutationApplied, TransactionErrorCode>
                            updated =
                                    JdbcValueTransactionService.updateItemPayload(
                                            connection, request.transactionId(), update);
                    if (updated
                            instanceof
                            Result.Failure<
                                            JdbcValueTransactionService.MutationApplied,
                                            TransactionErrorCode>
                                    failure) {
                        connection.rollback();
                        return Result.failure(failure.error(), failure.detail());
                    }
                    tool =
                            Optional.of(
                                    JdbcValueTransactionService.findItem(
                                                    connection, update.itemId())
                                            .orElseThrow(
                                                    () ->
                                                            new SQLException(
                                                                    "Committed node tool is missing.")));
                    checkpointObserver.reached(Checkpoint.AFTER_TOOL_MUTATION);
                }
                ArrayList<LotLocationRecord> outputs = new ArrayList<>();
                for (NewLotLocation output : commit.outputLots()) {
                    Result<JdbcValueTransactionService.MutationApplied, TransactionErrorCode>
                            inserted =
                                    JdbcValueTransactionService.insertLot(
                                            connection, request, output);
                    if (inserted
                            instanceof
                            Result.Failure<
                                            JdbcValueTransactionService.MutationApplied,
                                            TransactionErrorCode>
                                    failure) {
                        connection.rollback();
                        return Result.failure(failure.error(), failure.detail());
                    }
                    outputs.add(
                            JdbcValueTransactionService.findLot(connection, output.lotId())
                                    .orElseThrow(
                                            () ->
                                                    new SQLException(
                                                            "Committed harvest lot is missing.")));
                }
                if (!outputs.isEmpty()) {
                    checkpointObserver.reached(Checkpoint.AFTER_OUTPUT_MUTATION);
                }
                appendAudit(connection, request, commit, node);
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
                        new ResourceNodeStateCommitExecution(
                                node,
                                character,
                                tool,
                                outputs,
                                new TransactionExecution(journal, false)));
            } catch (SQLException exception) {
                JdbcTransactionJournalRepository.rollbackQuietly(connection);
                return JdbcTransactionJournalRepository.failure(exception);
            } catch (RuntimeException exception) {
                JdbcTransactionJournalRepository.rollbackQuietly(connection);
                throw exception;
            } finally {
                JdbcTransactionJournalRepository.restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    private static Result<ResourceNodeStateCommitExecution, TransactionErrorCode> validateRequest(
            TransactionRequest request, ResourceNodeStateCommit commit) {
        if (!request.operationType().equals(RESOURCE_NODE_STATE_COMMIT)) {
            return Result.failure(
                    TransactionErrorCode.TRANSACTION_OPERATION_MISMATCH,
                    "Transaction operation does not match a resource-node state commit.");
        }
        if (commit.actor().isPresent()) {
            if (request.characterId().filter(commit.actor().orElseThrow()::equals).isEmpty()
                    || request.sessionId().isEmpty()) {
                return Result.failure(
                        TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                        "Resource-node actor/session does not match the transaction.");
            }
        } else if (request.characterId().isPresent() || request.sessionId().isPresent()) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Resource-node recovery must use a system transaction.");
        }
        return null;
    }

    private static ResourceNodeStateCommitExecution execution(
            Connection connection,
            ResourceNodeStateCommit commit,
            TransactionJournalEntry journal,
            boolean replayed)
            throws SQLException {
        ResourceNodeStateRecord node =
                findNode(connection, commit.nodeId())
                        .orElseThrow(
                                () ->
                                        new SQLException(
                                                "Committed resource-node state is missing."));
        Optional<CharacterLifeskillStateRecord> character =
                commit.characterState().isPresent()
                        ? Optional.of(
                                findCharacter(
                                                connection,
                                                commit.characterState().orElseThrow().characterId())
                                        .orElseThrow(
                                                () ->
                                                        new SQLException(
                                                                "Committed Lifeskill state is missing.")))
                        : Optional.empty();
        Optional<ItemLocationRecord> tool =
                commit.toolUpdate().isPresent()
                        ? Optional.of(
                                JdbcValueTransactionService.findItem(
                                                connection,
                                                commit.toolUpdate().orElseThrow().itemId())
                                        .orElseThrow(
                                                () ->
                                                        new SQLException(
                                                                "Committed node tool is missing.")))
                        : Optional.empty();
        ArrayList<LotLocationRecord> outputs = new ArrayList<>();
        for (NewLotLocation output : commit.outputLots()) {
            outputs.add(
                    JdbcValueTransactionService.findLot(connection, output.lotId())
                            .orElseThrow(
                                    () -> new SQLException("Committed harvest lot is missing.")));
        }
        return new ResourceNodeStateCommitExecution(
                node, character, tool, outputs, new TransactionExecution(journal, replayed));
    }

    private static ResourceNodeStateRecord mutateNode(
            Connection connection, TransactionRequest request, ResourceNodeStateCommit commit)
            throws SQLException {
        String sql =
                commit.expectedNodeVersion() == 0
                        ? """
                        INSERT INTO resource_node_state(
                            node_id, definition_id, phase, state_payload, content_version,
                            version, last_transaction_id, created_at, updated_at
                        ) VALUES (?, ?, ?, CAST(? AS JSONB), ?, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON CONFLICT (node_id) DO NOTHING
                        RETURNING
                        """
                                + NODE_COLUMNS
                        : """
                        UPDATE resource_node_state
                        SET definition_id = ?, phase = ?, state_payload = CAST(? AS JSONB),
                            content_version = ?, version = version + 1,
                            last_transaction_id = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE node_id = ? AND version = ?
                        RETURNING
                        """
                                + NODE_COLUMNS;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (commit.expectedNodeVersion() == 0) {
                statement.setObject(1, commit.nodeId().value());
                statement.setString(2, commit.definitionId().value());
                statement.setString(3, commit.phase());
                statement.setString(4, commit.replacementNodePayloadJson());
                statement.setString(5, request.contentVersion());
                statement.setObject(6, request.transactionId().value());
            } else {
                statement.setString(1, commit.definitionId().value());
                statement.setString(2, commit.phase());
                statement.setString(3, commit.replacementNodePayloadJson());
                statement.setString(4, request.contentVersion());
                statement.setObject(5, request.transactionId().value());
                statement.setObject(6, commit.nodeId().value());
                statement.setLong(7, commit.expectedNodeVersion());
            }
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? readNode(row) : null;
            }
        }
    }

    private static CharacterLifeskillStateRecord mutateCharacter(
            Connection connection,
            TransactionRequest request,
            CharacterLifeskillStateMutation mutation)
            throws SQLException {
        String sql =
                mutation.expectedVersion() == 0
                        ? """
                        INSERT INTO character_lifeskill_state(
                            character_id, state_payload, content_version, version,
                            last_transaction_id, created_at, updated_at
                        ) VALUES (?, CAST(? AS JSONB), ?, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON CONFLICT (character_id) DO NOTHING
                        RETURNING
                        """
                                + CHARACTER_COLUMNS
                        : """
                        UPDATE character_lifeskill_state
                        SET state_payload = CAST(? AS JSONB), content_version = ?,
                            version = version + 1, last_transaction_id = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE character_id = ? AND version = ?
                        RETURNING
                        """
                                + CHARACTER_COLUMNS;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (mutation.expectedVersion() == 0) {
                statement.setObject(1, mutation.characterId().value());
                statement.setString(2, mutation.replacementPayloadJson());
                statement.setString(3, request.contentVersion());
                statement.setObject(4, request.transactionId().value());
            } else {
                statement.setString(1, mutation.replacementPayloadJson());
                statement.setString(2, request.contentVersion());
                statement.setObject(3, request.transactionId().value());
                statement.setObject(4, mutation.characterId().value());
                statement.setLong(5, mutation.expectedVersion());
            }
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? readCharacter(row) : null;
            }
        }
    }

    private static Optional<ResourceNodeStateRecord> findNode(
            Connection connection, ResourceNodeId nodeId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT " + NODE_COLUMNS + " FROM resource_node_state WHERE node_id = ?")) {
            statement.setObject(1, nodeId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readNode(row)) : Optional.empty();
            }
        }
    }

    private static Optional<CharacterLifeskillStateRecord> findCharacter(
            Connection connection, CharacterId characterId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT "
                                + CHARACTER_COLUMNS
                                + " FROM character_lifeskill_state WHERE character_id = ?")) {
            statement.setObject(1, characterId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readCharacter(row)) : Optional.empty();
            }
        }
    }

    private static ResourceNodeStateRecord readNode(ResultSet row) throws SQLException {
        return new ResourceNodeStateRecord(
                new ResourceNodeId(row.getObject("node_id", java.util.UUID.class)),
                DefinitionId.of(row.getString("definition_id")),
                row.getString("phase"),
                row.getString("state_payload"),
                row.getString("content_version"),
                row.getLong("version"),
                new TransactionId(row.getObject("last_transaction_id", java.util.UUID.class)),
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static CharacterLifeskillStateRecord readCharacter(ResultSet row) throws SQLException {
        return new CharacterLifeskillStateRecord(
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
            ResourceNodeStateCommit commit,
            ResourceNodeStateRecord node)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO audit_log(
                            transaction_id, actor_character_id, action_type,
                            subject_type, subject_id, details, created_at
                        ) VALUES (?, ?, ?, 'RESOURCE_NODE', ?, CAST(? AS JSONB), CURRENT_TIMESTAMP)
                        """)) {
            statement.setObject(1, request.transactionId().value());
            if (commit.actor().isPresent()) {
                statement.setObject(2, commit.actor().orElseThrow().value());
            } else {
                statement.setNull(2, java.sql.Types.OTHER);
            }
            statement.setString(3, RESOURCE_NODE_STATE_COMMIT + "." + commit.kind().name());
            statement.setObject(4, node.nodeId().value());
            statement.setString(
                    5,
                    "{\"version\":"
                            + node.version()
                            + ",\"phase\":\""
                            + escapeJson(node.phase())
                            + "\",\"outputs\":"
                            + commit.outputLots().size()
                            + "}");
            statement.executeUpdate();
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    enum Checkpoint {
        AFTER_NODE_MUTATION,
        AFTER_CHARACTER_MUTATION,
        AFTER_TOOL_MUTATION,
        AFTER_OUTPUT_MUTATION
    }

    @FunctionalInterface
    interface CheckpointObserver {
        void reached(Checkpoint checkpoint);
    }
}
