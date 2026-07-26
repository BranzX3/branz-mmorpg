package com.branz.mmorpg.storage;

import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.character.CharacterClassId;
import com.branz.mmorpg.api.character.CharacterClassSelectionRepository;
import com.branz.mmorpg.api.character.CharacterClassSelectionResult;
import com.branz.mmorpg.api.character.CharacterClassSnapshot;
import com.branz.mmorpg.api.character.CharacterClassState;
import com.branz.mmorpg.api.character.StarterGrantPlan;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.operation.OperationId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** MySQL exact-once transaction for the permanent class choice. */
public final class JdbcCharacterClassSelectionRepository
        implements CharacterClassSelectionRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Integer>> ITEM_MAP = new TypeReference<>() {};
    private final DatabaseManager database;

    public JdbcCharacterClassSelectionRepository(DatabaseManager database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public Optional<CharacterClassSelectionResult> find(UUID playerId, OperationId operationId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(operationId, "operationId");
        try {
            return database.inTransaction(connection -> Optional.ofNullable(
                    read(connection, playerId, operationId)));
        } catch (SQLException exception) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE,
                    "failed to read permanent class selection " + operationId, exception);
        }
    }

    @Override
    public CharacterClassSelectionResult select(
            UUID playerId, long expectedProfileRevision, OperationId operationId,
            CharacterClassDefinition definition, long contentRevision, Instant selectedAt) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(selectedAt, "selectedAt");
        if (!playerId.equals(operationId.playerUuid())) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT, "operation belongs to another player");
        }
        try {
            return database.inTransaction(connection -> {
                JdbcPlayerProfileRepository.lockPlayer(connection, playerId);
                CharacterClassSelectionResult existing = read(connection, playerId, operationId);
                if (existing != null) return existing;

                try (PreparedStatement profile = connection.prepareStatement(
                        "SELECT class_id, revision FROM mmorpg_player_profiles "
                                + "WHERE player_uuid = ? FOR UPDATE")) {
                    profile.setBytes(1, JdbcPlayerProfileRepository.toBytes(playerId));
                    try (ResultSet row = profile.executeQuery()) {
                        if (!row.next()) {
                            throw new MMOException(ErrorCode.PROFILE_LOAD_FAILED,
                                    "profile does not exist for " + playerId);
                        }
                        String selected = row.getString("class_id");
                        long revision = row.getLong("revision");
                        if (selected != null) {
                            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                                    "character class is permanent and already selected: " + selected);
                        }
                        if (revision != expectedProfileRevision) {
                            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                                    "stale profile revision: expected " + expectedProfileRevision
                                            + " but storage is " + revision);
                        }
                    }
                }

                long committedRevision = expectedProfileRevision + 1;
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE mmorpg_player_profiles SET class_id = ?, class_selected_at = ?, "
                                + "class_selection_operation_id = ?, class_schema_version = ?, "
                                + "revision = revision + 1 WHERE player_uuid = ? "
                                + "AND class_id IS NULL AND revision = ?")) {
                    update.setString(1, definition.id().toString());
                    update.setTimestamp(2, Timestamp.from(selectedAt));
                    update.setString(3, operationId.value());
                    update.setInt(4, definition.schemaVersion());
                    update.setBytes(5, JdbcPlayerProfileRepository.toBytes(playerId));
                    update.setLong(6, expectedProfileRevision);
                    if (update.executeUpdate() != 1) {
                        throw new MMOException(ErrorCode.STORAGE_FAILURE,
                                "class selection lost its profile lock for " + playerId);
                    }
                }

                StarterGrantPlan starter = definition.starterGrantPlan();
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO mmorpg_character_class_selection "
                                + "(player_uuid, operation_id, class_id, class_schema_version, selected_at, "
                                + "content_revision, profile_revision, starter_plan_id, starter_plan_revision, "
                                + "starter_weapon_id, starter_skill_ids, starter_additional_items) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON))")) {
                    insert.setBytes(1, JdbcPlayerProfileRepository.toBytes(playerId));
                    insert.setString(2, operationId.value());
                    insert.setString(3, definition.id().toString());
                    insert.setInt(4, definition.schemaVersion());
                    insert.setTimestamp(5, Timestamp.from(selectedAt));
                    insert.setLong(6, contentRevision);
                    insert.setLong(7, committedRevision);
                    insert.setString(8, starter.id().toString());
                    insert.setInt(9, starter.revision());
                    insert.setString(10, starter.weaponId().toString());
                    insert.setString(11, encode(starter.unlockedSkillIds().stream()
                            .map(ContentId::toString).toList()));
                    Map<String, Integer> items = new LinkedHashMap<>();
                    starter.additionalItems().forEach((id, amount) -> items.put(id.toString(), amount));
                    insert.setString(12, encode(items));
                    insert.executeUpdate();
                }
                try (PreparedStatement delivery = connection.prepareStatement(
                        "INSERT INTO mmorpg_starter_kit_delivery "
                                + "(player_uuid, selection_operation_id, starter_plan_id, "
                                + "starter_plan_revision, starter_weapon_id, "
                                + "starter_additional_items, state, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), 'PENDING', ?)")) {
                    Map<String, Integer> items = new LinkedHashMap<>();
                    starter.additionalItems().forEach(
                            (id, amount) -> items.put(id.toString(), amount));
                    delivery.setBytes(1, JdbcPlayerProfileRepository.toBytes(playerId));
                    delivery.setString(2, operationId.value());
                    delivery.setString(3, starter.id().toString());
                    delivery.setInt(4, starter.revision());
                    delivery.setString(5, starter.weaponId().toString());
                    delivery.setString(6, encode(items));
                    delivery.setTimestamp(7, Timestamp.from(selectedAt));
                    delivery.executeUpdate();
                }
                try (PreparedStatement audit = connection.prepareStatement(
                        "INSERT INTO mmorpg_audit_log "
                                + "(actor_uuid, action, subject, detail_json) "
                                + "VALUES (?, 'character_class_selected', ?, JSON_OBJECT(" 
                                + "'class_id', ?, 'content_revision', ?, 'starter_revision', ?))")) {
                    audit.setBytes(1, JdbcPlayerProfileRepository.toBytes(playerId));
                    audit.setString(2, operationId.value());
                    audit.setString(3, definition.id().toString());
                    audit.setLong(4, contentRevision);
                    audit.setInt(5, starter.revision());
                    audit.executeUpdate();
                }
                return result(CharacterClassSelectionResult.Status.APPLIED, playerId, operationId,
                        definition.classId(), definition.schemaVersion(), selectedAt,
                        committedRevision, starter, contentRevision);
            });
        } catch (SQLException exception) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE,
                    "failed permanent class selection " + operationId, exception);
        }
    }

    private static CharacterClassSelectionResult read(
            java.sql.Connection connection, UUID playerId, OperationId requestedOperation)
            throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT operation_id, class_id, class_schema_version, selected_at, content_revision, "
                        + "profile_revision, starter_plan_id, starter_plan_revision, starter_weapon_id, "
                        + "starter_skill_ids, starter_additional_items "
                        + "FROM mmorpg_character_class_selection WHERE player_uuid = ?")) {
            select.setBytes(1, JdbcPlayerProfileRepository.toBytes(playerId));
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) return null;
                OperationId storedOperation = OperationId.parse(row.getString("operation_id"));
                if (!storedOperation.equals(requestedOperation)) {
                    throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                            "character class was already selected by " + storedOperation);
                }
                List<ContentId> skills = decode(row.getString("starter_skill_ids"), STRING_LIST)
                        .stream().map(ContentId::parse).toList();
                Map<ContentId, Integer> items = new LinkedHashMap<>();
                decode(row.getString("starter_additional_items"), ITEM_MAP)
                        .forEach((id, amount) -> items.put(ContentId.parse(id), amount));
                StarterGrantPlan starter = new StarterGrantPlan(
                        ContentId.parse(row.getString("starter_plan_id")),
                        row.getInt("starter_plan_revision"),
                        ContentId.parse(row.getString("starter_weapon_id")), skills, items);
                return result(CharacterClassSelectionResult.Status.REPLAYED, playerId,
                        storedOperation, CharacterClassId.parse(row.getString("class_id")),
                        row.getInt("class_schema_version"), row.getTimestamp("selected_at").toInstant(),
                        row.getLong("profile_revision"), starter, row.getLong("content_revision"));
            }
        }
    }

    private static CharacterClassSelectionResult result(
            CharacterClassSelectionResult.Status status, UUID playerId, OperationId operation,
            CharacterClassId classId, int schemaVersion, Instant selectedAt, long profileRevision,
            StarterGrantPlan starter, long contentRevision) {
        CharacterClassSnapshot snapshot = new CharacterClassSnapshot(playerId,
                CharacterClassState.CLASS_SELECTED, Optional.of(classId), Optional.of(selectedAt),
                Optional.of(operation), schemaVersion, profileRevision);
        return new CharacterClassSelectionResult(status, snapshot, starter, contentRevision);
    }

    private static String encode(Object value) throws SQLException {
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new SQLException("cannot encode starter plan", exception); }
    }

    private static <T> T decode(String value, TypeReference<T> type) throws SQLException {
        try { return JSON.readValue(value, type); }
        catch (JsonProcessingException exception) { throw new SQLException("cannot decode starter plan", exception); }
    }
}
