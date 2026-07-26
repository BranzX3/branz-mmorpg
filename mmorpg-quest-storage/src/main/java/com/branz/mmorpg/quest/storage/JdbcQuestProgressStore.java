package com.branz.mmorpg.quest.storage;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.quest.api.ActionDefinition;
import com.branz.mmorpg.quest.api.ObjectiveProgress;
import com.branz.mmorpg.quest.api.PendingQuestOperation;
import com.branz.mmorpg.quest.api.QuestCommit;
import com.branz.mmorpg.quest.api.QuestProgress;
import com.branz.mmorpg.quest.api.QuestProgressStore;
import com.branz.mmorpg.quest.api.QuestState;
import com.branz.mmorpg.storage.DatabaseManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

public final class JdbcQuestProgressStore implements QuestProgressStore {
    private static final TypeReference<Map<String, ObjectiveProgress>> OBJECTIVES =
            new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> STRINGS =
            new TypeReference<>() {};
    private final DatabaseManager database;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcQuestProgressStore(DatabaseManager database) {
        this.database = java.util.Objects.requireNonNull(database, "database");
    }

    @Override public Optional<QuestProgress> load(UUID playerId, ContentId questId) {
        try {
            return database.inTransaction(connection ->
                    read(connection, playerId, questId, false));
        } catch (SQLException failure) {
            throw storage("failed to load quest progress", failure);
        }
    }

    @Override public Collection<QuestProgress> active(UUID playerId) {
        try {
            return database.inTransaction(connection -> {
                ArrayList<ContentId> ids = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT quest_id FROM quest_progress WHERE player_uuid = ? "
                                + "AND quest_state IN ('ACTIVE','READY_TO_TURN_IN','COMPLETING',"
                                + "'MIGRATION_REQUIRED') ORDER BY updated_at")) {
                    statement.setBytes(1, bytes(playerId));
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) ids.add(ContentId.parse(rows.getString(1)));
                    }
                }
                ArrayList<QuestProgress> result = new ArrayList<>();
                for (ContentId id : ids) result.add(
                        read(connection, playerId, id, false).orElseThrow());
                return java.util.List.copyOf(result);
            });
        } catch (SQLException failure) {
            throw storage("failed to load active quests", failure);
        }
    }

    @Override public Collection<QuestProgress> active(
            UUID playerId, Set<ContentId> questCandidates) {
        if (questCandidates.isEmpty()) return java.util.List.of();
        try {
            return database.inTransaction(connection -> {
                String placeholders = String.join(",",
                        java.util.Collections.nCopies(questCandidates.size(), "?"));
                ArrayList<ContentId> ids = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT quest_id FROM quest_progress WHERE player_uuid = ? "
                                + "AND quest_state = 'ACTIVE' AND quest_id IN ("
                                + placeholders + ") ORDER BY updated_at")) {
                    statement.setBytes(1, bytes(playerId));
                    int index = 2;
                    for (ContentId id : questCandidates) {
                        statement.setString(index++, id.toString());
                    }
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) ids.add(ContentId.parse(rows.getString(1)));
                    }
                }
                ArrayList<QuestProgress> result = new ArrayList<>();
                for (ContentId id : ids) {
                    read(connection, playerId, id, false).ifPresent(result::add);
                }
                return java.util.List.copyOf(result);
            });
        } catch (SQLException failure) {
            throw storage("failed to load objective candidates", failure);
        }
    }

    @Override public QuestCommit insert(
            QuestProgress progress, Collection<PendingQuestOperation> operations) {
        try {
            return database.inTransaction(connection -> {
                Optional<QuestProgress> existing =
                        read(connection, progress.playerId(), progress.questId(), true);
                if (existing.isPresent() && existing.orElseThrow().state() != QuestState.COMPLETED
                        && existing.orElseThrow().state() != QuestState.ABANDONED
                        && existing.orElseThrow().state() != QuestState.FAILED) {
                    throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                            "quest already has active progress");
                }
                if (existing.isPresent()) update(connection, progress);
                else insertProgress(connection, progress);
                insertOperations(connection, operations);
                return new QuestCommit(true, progress);
            });
        } catch (SQLException failure) {
            throw storage("failed to insert quest progress", failure);
        }
    }

    @Override public QuestCommit commit(
            QuestProgress before, QuestProgress after, UUID eventId,
            Collection<PendingQuestOperation> operations) {
        try {
            return database.inTransaction(connection -> {
                QuestProgress current = read(
                        connection, before.playerId(), before.questId(), true).orElseThrow();
                if (!claimEvent(connection, before.playerId(), before.questId(), eventId)) {
                    return new QuestCommit(false, current);
                }
                if (current.revision() != before.revision()
                        || after.revision() != before.revision() + 1) {
                    throw new MMOException(ErrorCode.STORAGE_FAILURE,
                            "quest optimistic revision conflict");
                }
                update(connection, after);
                insertOperations(connection, operations);
                if (terminal(after.state())) history(connection, after);
                return new QuestCommit(true, after);
            });
        } catch (SQLException failure) {
            throw storage("failed to commit quest progress", failure);
        }
    }

    @Override public Collection<PendingQuestOperation> pending(Instant dueAt, int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("invalid limit");
        try {
            return database.inTransaction(connection -> {
                ArrayList<PendingQuestOperation> result = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT operation_id, player_uuid, quest_id, operation_type, payload_json, "
                                + "operation_state, attempts, next_attempt_at, last_error "
                                + "FROM quest_pending_operations WHERE operation_state = 'PENDING' "
                                + "AND next_attempt_at <= ? ORDER BY next_attempt_at, "
                                + "player_uuid, quest_id, CAST(JSON_UNQUOTE(JSON_EXTRACT("
                                + "payload_json, '$._order')) AS UNSIGNED) LIMIT ?")) {
                    statement.setTimestamp(1, Timestamp.from(dueAt));
                    statement.setInt(2, limit);
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) result.add(readOperation(rows));
                    }
                }
                return java.util.List.copyOf(result);
            });
        } catch (SQLException failure) {
            throw storage("failed to load quest operations", failure);
        }
    }

    @Override public void completeOperation(String operationId) {
        operationState(operationId, "COMPLETE", "", Instant.EPOCH, false);
    }

    @Override public void failOperation(String operationId, String error, Instant retryAt) {
        operationState(operationId, "PENDING",
                error == null ? "unknown failure" : error, retryAt, true);
    }

    @Override public boolean hasIncompleteOperations(UUID playerId, ContentId questId) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM quest_pending_operations WHERE player_uuid = ? "
                                + "AND quest_id = ? AND operation_state <> 'COMPLETE' LIMIT 1")) {
                    statement.setBytes(1, bytes(playerId));
                    statement.setString(2, questId.toString());
                    try (ResultSet row = statement.executeQuery()) {
                        return row.next();
                    }
                }
            });
        } catch (SQLException failure) {
            throw storage("failed to inspect quest operations", failure);
        }
    }

    @Override public boolean hasIncompleteRequiredOperations(
            UUID playerId, ContentId questId) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM quest_pending_operations "
                                + "WHERE player_uuid = ? AND quest_id = ? "
                                + "AND operation_state <> 'COMPLETE' "
                                + "AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.required')) "
                                + "= 'true' LIMIT 1")) {
                    statement.setBytes(1, bytes(playerId));
                    statement.setString(2, questId.toString());
                    try (ResultSet row = statement.executeQuery()) {
                        return row.next();
                    }
                }
            });
        } catch (SQLException failure) {
            throw storage("failed to inspect required quest operations", failure);
        }
    }

    @Override public boolean hasEarlierIncompleteRequiredOperation(
            PendingQuestOperation operation) {
        int order = Integer.parseInt(
                operation.payload().getOrDefault("_order", "0"));
        if (order == 0) return false;
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM quest_pending_operations "
                                + "WHERE player_uuid = ? AND quest_id = ? "
                                + "AND operation_state <> 'COMPLETE' "
                                + "AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.required')) "
                                + "= 'true' AND CAST(JSON_UNQUOTE(JSON_EXTRACT("
                                + "payload_json, '$._order')) AS UNSIGNED) < ? LIMIT 1")) {
                    statement.setBytes(1, bytes(operation.playerId()));
                    statement.setString(2, operation.questId().toString());
                    statement.setInt(3, order);
                    try (ResultSet row = statement.executeQuery()) {
                        return row.next();
                    }
                }
            });
        } catch (SQLException failure) {
            throw storage("failed to inspect quest operation ordering", failure);
        }
    }

    @Override public boolean reset(
            UUID playerId, ContentId questId, UUID actorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("quest reset reason is required");
        }
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement deleteOps = connection.prepareStatement(
                        "DELETE FROM quest_pending_operations "
                                + "WHERE player_uuid = ? AND quest_id = ?")) {
                    deleteOps.setBytes(1, bytes(playerId));
                    deleteOps.setString(2, questId.toString());
                    deleteOps.executeUpdate();
                }
                boolean removed;
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM quest_progress WHERE player_uuid = ? AND quest_id = ?")) {
                    delete.setBytes(1, bytes(playerId));
                    delete.setString(2, questId.toString());
                    removed = delete.executeUpdate() == 1;
                }
                try (PreparedStatement audit = connection.prepareStatement(
                        "INSERT INTO mmorpg_audit_log "
                                + "(actor_uuid, action, subject, detail_json) "
                                + "VALUES (?, 'quest_reset', ?, JSON_OBJECT('reason', ?))")) {
                    audit.setBytes(1, bytes(actorId));
                    audit.setString(2, playerId + ":" + questId);
                    audit.setString(3, reason);
                    audit.executeUpdate();
                }
                return removed;
            });
        } catch (SQLException failure) {
            throw storage("failed to reset quest progress", failure);
        }
    }

    @Override public QuestProgress migrate(
            QuestProgress before, QuestProgress after, UUID actorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("quest migration reason is required");
        }
        try {
            return database.inTransaction(connection -> {
                QuestProgress locked = read(connection, before.playerId(),
                        before.questId(), true).orElseThrow(() ->
                        new MMOException(ErrorCode.INVALID_ARGUMENT,
                                "quest progress does not exist"));
                if (locked.revision() != before.revision()
                        || locked.definitionVersion() != before.definitionVersion()) {
                    throw new MMOException(ErrorCode.STORAGE_FAILURE,
                            "quest progress changed during migration");
                }
                update(connection, after);
                try (PreparedStatement audit = connection.prepareStatement(
                        "INSERT INTO mmorpg_audit_log "
                                + "(actor_uuid, action, subject, detail_json) VALUES "
                                + "(?, 'quest_migrate', ?, JSON_OBJECT("
                                + "'reason', ?, 'from_version', ?, 'to_version', ?))")) {
                    audit.setBytes(1, bytes(actorId));
                    audit.setString(2, before.playerId() + ":" + before.questId());
                    audit.setString(3, reason);
                    audit.setInt(4, before.definitionVersion());
                    audit.setInt(5, after.definitionVersion());
                    audit.executeUpdate();
                }
                return after;
            });
        } catch (SQLException failure) {
            throw storage("failed to migrate quest progress", failure);
        }
    }

    @Override public QuestProgress repair(
            QuestProgress before, QuestProgress after,
            Collection<PendingQuestOperation> operations,
            UUID actorId, String action, String reason) {
        if (reason == null || reason.isBlank()
                || !java.util.Set.of("quest_stage", "quest_objective").contains(action)) {
            throw new IllegalArgumentException("invalid audited quest repair");
        }
        try {
            return database.inTransaction(connection -> {
                QuestProgress locked = read(connection, before.playerId(),
                        before.questId(), true).orElseThrow(() ->
                        new MMOException(ErrorCode.INVALID_ARGUMENT,
                                "quest progress does not exist"));
                if (locked.revision() != before.revision()) {
                    throw new MMOException(ErrorCode.STORAGE_FAILURE,
                            "quest progress changed during repair");
                }
                update(connection, after);
                insertOperations(connection, operations);
                try (PreparedStatement audit = connection.prepareStatement(
                        "INSERT INTO mmorpg_audit_log "
                                + "(actor_uuid, action, subject, detail_json) VALUES "
                                + "(?, ?, ?, JSON_OBJECT('reason', ?, 'before_revision', ?, "
                                + "'after_revision', ?))")) {
                    audit.setBytes(1, bytes(actorId));
                    audit.setString(2, action);
                    audit.setString(3, before.playerId() + ":" + before.questId());
                    audit.setString(4, reason);
                    audit.setLong(5, before.revision());
                    audit.setLong(6, after.revision());
                    audit.executeUpdate();
                }
                return after;
            });
        } catch (SQLException failure) {
            throw storage("failed to repair quest progress", failure);
        }
    }

    private void operationState(String id, String state, String error,
                                Instant retry, boolean increment) {
        try {
            database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE quest_pending_operations SET operation_state = ?, "
                                + "last_error = ?, next_attempt_at = ?, attempts = attempts + ? "
                                + "WHERE operation_id = ?")) {
                    statement.setString(1, state);
                    statement.setString(2, truncate(error, 512));
                    statement.setTimestamp(3, Timestamp.from(retry));
                    statement.setInt(4, increment ? 1 : 0);
                    statement.setString(5, id);
                    if (statement.executeUpdate() != 1) {
                        throw new MMOException(
                                ErrorCode.INVALID_ARGUMENT, "unknown quest operation " + id);
                    }
                }
                return null;
            });
        } catch (SQLException failure) {
            throw storage("failed to update quest operation", failure);
        }
    }

    private Optional<QuestProgress> read(
            Connection connection, UUID playerId, ContentId questId, boolean lock)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT definition_version, progress_revision, quest_state, stage_id, "
                        + "occurrence_uuid, objective_state_json, flags_json, started_at, "
                        + "updated_at, completed_at FROM quest_progress "
                        + "WHERE player_uuid = ? AND quest_id = ?"
                        + (lock ? " FOR UPDATE" : ""))) {
            statement.setBytes(1, bytes(playerId));
            statement.setString(2, questId.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                Timestamp completed = row.getTimestamp("completed_at");
                return Optional.of(new QuestProgress(playerId, questId,
                        row.getInt("definition_version"), row.getLong("progress_revision"),
                        QuestState.valueOf(row.getString("quest_state")),
                        row.getString("stage_id"), uuid(row.getBytes("occurrence_uuid")),
                        json(row.getString("objective_state_json"), OBJECTIVES),
                        json(row.getString("flags_json"), STRINGS),
                        row.getTimestamp("started_at").toInstant(),
                        row.getTimestamp("updated_at").toInstant(),
                        completed == null ? Optional.empty()
                                : Optional.of(completed.toInstant())));
            }
        }
    }

    private void insertProgress(Connection connection, QuestProgress value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO quest_progress (player_uuid, quest_id, definition_version, "
                        + "progress_revision, quest_state, stage_id, occurrence_uuid, "
                        + "objective_state_json, flags_json, started_at, updated_at, completed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            bindProgress(statement, value);
            statement.executeUpdate();
        }
    }

    private void update(Connection connection, QuestProgress value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE quest_progress SET definition_version = ?, progress_revision = ?, "
                        + "quest_state = ?, stage_id = ?, occurrence_uuid = ?, "
                        + "objective_state_json = ?, flags_json = ?, started_at = ?, "
                        + "updated_at = ?, completed_at = ? WHERE player_uuid = ? AND quest_id = ?")) {
            statement.setInt(1, value.definitionVersion());
            statement.setLong(2, value.revision());
            statement.setString(3, value.state().name());
            statement.setString(4, value.stageId());
            statement.setBytes(5, bytes(value.occurrenceId()));
            statement.setString(6, json(value.objectives()));
            statement.setString(7, json(value.flags()));
            statement.setTimestamp(8, Timestamp.from(value.startedAt()));
            statement.setTimestamp(9, Timestamp.from(value.updatedAt()));
            if (value.completedAt().isPresent()) {
                statement.setTimestamp(10, Timestamp.from(value.completedAt().orElseThrow()));
            } else statement.setNull(10, java.sql.Types.TIMESTAMP);
            statement.setBytes(11, bytes(value.playerId()));
            statement.setString(12, value.questId().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("quest progress disappeared");
            }
        }
    }

    private void bindProgress(PreparedStatement statement, QuestProgress value)
            throws SQLException {
        statement.setBytes(1, bytes(value.playerId()));
        statement.setString(2, value.questId().toString());
        statement.setInt(3, value.definitionVersion());
        statement.setLong(4, value.revision());
        statement.setString(5, value.state().name());
        statement.setString(6, value.stageId());
        statement.setBytes(7, bytes(value.occurrenceId()));
        statement.setString(8, json(value.objectives()));
        statement.setString(9, json(value.flags()));
        statement.setTimestamp(10, Timestamp.from(value.startedAt()));
        statement.setTimestamp(11, Timestamp.from(value.updatedAt()));
        if (value.completedAt().isPresent()) {
            statement.setTimestamp(12, Timestamp.from(value.completedAt().orElseThrow()));
        } else statement.setNull(12, java.sql.Types.TIMESTAMP);
    }

    private void insertOperations(
            Connection connection, Collection<PendingQuestOperation> operations)
            throws SQLException {
        if (operations.isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO quest_pending_operations "
                        + "(operation_id, player_uuid, quest_id, operation_type, payload_json, "
                        + "operation_state, attempts, next_attempt_at, last_error) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (PendingQuestOperation operation : operations) {
                statement.setString(1, operation.operationId());
                statement.setBytes(2, bytes(operation.playerId()));
                statement.setString(3, operation.questId().toString());
                statement.setString(4, operation.operationType().name());
                statement.setString(5, json(operation.payload()));
                statement.setString(6, operation.state().name());
                statement.setInt(7, operation.attempts());
                statement.setTimestamp(8, Timestamp.from(operation.nextAttemptAt()));
                statement.setString(9, truncate(operation.lastError(), 512));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private boolean claimEvent(
            Connection connection, UUID playerId,
            ContentId questId, UUID eventId) throws SQLException {
        int bucket = (int) (Instant.now().atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() / 7);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO quest_processed_events "
                        + "(player_uuid, quest_id, event_uuid, processed_at, expiry_bucket) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), ?)")) {
            statement.setBytes(1, bytes(playerId));
            statement.setString(2, questId.toString());
            statement.setBytes(3, bytes(eventId));
            statement.setInt(4, bucket);
            return statement.executeUpdate() == 1;
        }
    }

    private void history(Connection connection, QuestProgress value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO quest_history "
                        + "(player_uuid, quest_id, occurrence_uuid, outcome, started_at, completed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setBytes(1, bytes(value.playerId()));
            statement.setString(2, value.questId().toString());
            statement.setBytes(3, bytes(value.occurrenceId()));
            statement.setString(4, value.state().name());
            statement.setTimestamp(5, Timestamp.from(value.startedAt()));
            statement.setTimestamp(6, Timestamp.from(
                    value.completedAt().orElse(value.updatedAt())));
            statement.executeUpdate();
        }
    }

    private PendingQuestOperation readOperation(ResultSet row) throws SQLException {
        return new PendingQuestOperation(row.getString("operation_id"),
                uuid(row.getBytes("player_uuid")), ContentId.parse(row.getString("quest_id")),
                ActionDefinition.Type.valueOf(row.getString("operation_type")),
                json(row.getString("payload_json"), STRINGS),
                PendingQuestOperation.State.valueOf(row.getString("operation_state")),
                row.getInt("attempts"), row.getTimestamp("next_attempt_at").toInstant(),
                row.getString("last_error"));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE, "quest JSON encode failed", failure);
        }
    }
    private <T> T json(String value, TypeReference<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException failure) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE, "quest JSON decode failed", failure);
        }
    }
    private static boolean terminal(QuestState state) {
        return state == QuestState.COMPLETED || state == QuestState.FAILED
                || state == QuestState.ABANDONED;
    }
    private static String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
    private static byte[] bytes(UUID value) {
        return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits()).array();
    }
    private static UUID uuid(byte[] value) {
        ByteBuffer buffer = ByteBuffer.wrap(value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
    private static MMOException storage(String message, SQLException failure) {
        return new MMOException(ErrorCode.STORAGE_FAILURE, message, failure);
    }
}
