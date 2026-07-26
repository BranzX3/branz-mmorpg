package com.branz.mmorpg.storage;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.lifeskill.LifeSkillProfile;
import com.branz.mmorpg.api.lifeskill.LifeSkillProgress;
import com.branz.mmorpg.api.lifeskill.LifeSkillSnapshot;
import com.branz.mmorpg.api.lifeskill.LifeSkillMutationCommit;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.player.PlayerProfileRepository;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * JDBC {@link PlayerProfileRepository}.
 *
 * <p>UUIDs are stored as BINARY(16) to keep the primary key narrow; these tables
 * are read on every login.
 */
public final class JdbcPlayerProfileRepository implements PlayerProfileRepository {
    private static final TypeReference<Map<String, String>> SETTINGS_TYPE = new TypeReference<>() {};
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DatabaseManager databaseManager;

    public JdbcPlayerProfileRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public PlayerProfile loadOrCreate(UUID playerId, String currentName) {
        Objects.requireNonNull(playerId, "playerId");
        try {
            return databaseManager.inTransaction(connection -> {
                PlayerProfile existing = readProfile(connection, playerId);
                if (existing != null) {
                    return existing;
                }
                Instant now = Instant.now();
                // Insert-if-absent: two backends racing a first login cannot
                // produce two rows, and the loser re-reads the winner's row.
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT IGNORE INTO mmorpg_player_profiles "
                                + "(player_uuid, last_known_name, schema_version, created_at, last_seen_at, "
                                + "settings_json, revision) VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), 0)")) {
                    insert.setBytes(1, toBytes(playerId));
                    insert.setString(2, currentName == null ? "" : currentName);
                    insert.setInt(3, PlayerProfile.CURRENT_SCHEMA_VERSION);
                    insert.setTimestamp(4, Timestamp.from(now));
                    insert.setTimestamp(5, Timestamp.from(now));
                    insert.setString(6, "{}");
                    insert.executeUpdate();
                }
                PlayerProfile created = readProfile(connection, playerId);
                if (created == null) {
                    throw new MMOException(ErrorCode.STORAGE_FAILURE,
                            "profile for " + playerId + " vanished immediately after insert");
                }
                return created;
            });
        } catch (SQLException exception) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE, "failed to load profile " + playerId, exception);
        }
    }

    @Override
    public LifeSkillProfile loadLifeSkills(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        try {
            return databaseManager.inTransaction(connection -> readLifeSkills(connection, playerId));
        } catch (SQLException exception) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE,
                    "failed to load life skills for " + playerId, exception);
        }
    }

    @Override
    public LifeSkillMutationCommit mutateLifeSkill(
            UUID playerId, ContentId skillId, OperationId operationId,
            UnaryOperator<LifeSkillSnapshot> mutation) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(mutation, "mutation");
        if (!playerId.equals(operationId.playerUuid())) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "operation player does not match mutation player");
        }
        try {
            return databaseManager.inTransaction(connection -> {
                lockPlayer(connection, playerId);
                LifeSkillProfile currentProfile = readLifeSkills(connection, playerId);
                LifeSkillSnapshot before = currentProfile.skill(skillId);
                try (PreparedStatement claim = connection.prepareStatement(
                        "INSERT IGNORE INTO mmorpg_processed_operation "
                                + "(operation_id, player_uuid, subsystem) VALUES (?, ?, ?)")) {
                    claim.setString(1, operationId.value());
                    claim.setBytes(2, toBytes(playerId));
                    claim.setString(3, operationId.subsystem());
                    if (claim.executeUpdate() == 0) {
                        return new LifeSkillMutationCommit(false, before, before);
                    }
                }
                LifeSkillSnapshot after = Objects.requireNonNull(
                        mutation.apply(before), "Life Skill mutation returned null");
                if (!after.skillId().equals(skillId)) {
                    throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                            "Life Skill mutation changed the skill ID");
                }
                writeLifeSkills(connection, currentProfile.with(after));
                try (PreparedStatement audit = connection.prepareStatement(
                        "INSERT INTO mmorpg_audit_log (actor_uuid, action, subject) VALUES (?, ?, ?)")) {
                    audit.setBytes(1, toBytes(playerId));
                    audit.setString(2, "life_skill_mutation");
                    audit.setString(3, operationId.value());
                    audit.executeUpdate();
                }
                return new LifeSkillMutationCommit(true, before, after);
            });
        } catch (SQLException exception) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE,
                    "failed Life Skill operation " + operationId, exception);
        }
    }

    @Override
    public void saveProfile(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        try {
            databaseManager.inTransaction(connection -> {
                writeProfile(connection, profile);
                return null;
            });
        } catch (SQLException exception) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE,
                    "failed to save profile " + profile.playerId(), exception);
        }
    }

    @Override
    public void saveLifeSkills(LifeSkillProfile lifeSkills) {
        Objects.requireNonNull(lifeSkills, "lifeSkills");
        try {
            databaseManager.inTransaction(connection -> {
                writeLifeSkills(connection, lifeSkills);
                return null;
            });
        } catch (SQLException exception) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE,
                    "failed to save life skills for " + lifeSkills.playerId(), exception);
        }
    }

    @Override
    public void saveSession(PlayerProfile profile, LifeSkillProfile lifeSkills) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(lifeSkills, "lifeSkills");
        if (!profile.playerId().equals(lifeSkills.playerId())) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "profile and Life Skill snapshot belong to different players");
        }
        try {
            databaseManager.inTransaction(connection -> {
                writeProfile(connection, profile);
                writeLifeSkills(connection, lifeSkills);
                return null;
            });
        } catch (SQLException exception) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE,
                    "failed to save session " + profile.playerId(), exception);
        }
    }

    private static void writeProfile(Connection connection, PlayerProfile profile) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE mmorpg_player_profiles SET last_known_name = ?, schema_version = ?, "
                        + "last_seen_at = ?, class_id = ?, selected_loadout_id = ?, respawn_point_id = ?, "
                        + "settings_json = CAST(? AS JSON), revision = revision + 1 "
                        + "WHERE player_uuid = ? AND revision = ?")) {
            update.setString(1, profile.lastKnownName());
            update.setInt(2, profile.schemaVersion());
            update.setTimestamp(3, Timestamp.from(profile.lastSeenAt()));
            update.setString(4, profile.classId().map(ContentId::toString).orElse(null));
            update.setString(5, profile.selectedLoadoutId().map(ContentId::toString).orElse(null));
            update.setString(6, profile.respawnPointId().map(ContentId::toString).orElse(null));
            update.setString(7, encodeSettings(profile.settings()));
            update.setBytes(8, toBytes(profile.playerId()));
            update.setLong(9, profile.revision());
            if (update.executeUpdate() == 0) {
                throw new MMOException(ErrorCode.STORAGE_FAILURE,
                        "profile save conflict for " + profile.playerId() + " at revision "
                                + profile.revision() + "; refusing to overwrite a newer session");
            }
        }
    }

    static void writeLifeSkills(Connection connection, LifeSkillProfile lifeSkills)
            throws SQLException {
        byte[] playerId = toBytes(lifeSkills.playerId());
        try (PreparedStatement deleteRanks = connection.prepareStatement(
                "DELETE FROM mmorpg_life_skill_node_rank WHERE player_uuid = ?");
             PreparedStatement deleteProgress = connection.prepareStatement(
                     "DELETE FROM mmorpg_life_skill_progress WHERE player_uuid = ?")) {
            deleteRanks.setBytes(1, playerId);
            deleteRanks.executeUpdate();
            deleteProgress.setBytes(1, playerId);
            deleteProgress.executeUpdate();
        }

        if (lifeSkills.skills().isEmpty()) {
            return;
        }
        try (PreparedStatement insertProgress = connection.prepareStatement(
                "INSERT INTO mmorpg_life_skill_progress "
                        + "(player_uuid, skill_id, level, total_xp, unspent_points, tree_revision, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)");
             PreparedStatement insertRank = connection.prepareStatement(
                     "INSERT INTO mmorpg_life_skill_node_rank "
                             + "(player_uuid, skill_id, node_id, rank_value, updated_at) "
                             + "VALUES (?, ?, ?, ?, ?)")) {
            for (LifeSkillSnapshot snapshot : lifeSkills.skills().values()) {
                LifeSkillProgress progress = snapshot.progress();
                insertProgress.setBytes(1, playerId);
                insertProgress.setString(2, progress.skillId().toString());
                insertProgress.setInt(3, progress.level());
                insertProgress.setLong(4, progress.totalXp());
                insertProgress.setInt(5, progress.unspentPoints());
                insertProgress.setLong(6, progress.treeRevision());
                insertProgress.setTimestamp(7, Timestamp.from(progress.updatedAt()));
                insertProgress.addBatch();

                for (Map.Entry<ContentId, Integer> rank : snapshot.nodeRanks().entrySet()) {
                    insertRank.setBytes(1, playerId);
                    insertRank.setString(2, progress.skillId().toString());
                    insertRank.setString(3, rank.getKey().toString());
                    insertRank.setInt(4, rank.getValue());
                    insertRank.setTimestamp(5, Timestamp.from(progress.updatedAt()));
                    insertRank.addBatch();
                }
            }
            insertProgress.executeBatch();
            insertRank.executeBatch();
        }
    }

    private static PlayerProfile readProfile(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT last_known_name, schema_version, created_at, last_seen_at, "
                        + "class_id, selected_loadout_id, respawn_point_id, settings_json, revision "
                        + "FROM mmorpg_player_profiles WHERE player_uuid = ?")) {
            select.setBytes(1, toBytes(playerId));
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new PlayerProfile(
                        playerId,
                        rows.getString("last_known_name"),
                        rows.getInt("schema_version"),
                        rows.getTimestamp("created_at").toInstant(),
                        rows.getTimestamp("last_seen_at").toInstant(),
                        optionalContentId(rows.getString("class_id")),
                        optionalContentId(rows.getString("selected_loadout_id")),
                        optionalContentId(rows.getString("respawn_point_id")),
                        decodeSettings(rows.getString("settings_json")),
                        rows.getLong("revision"));
            }
        }
    }

    private static Map<ContentId, Map<ContentId, Integer>> readNodeRanks(Connection connection, UUID playerId)
            throws SQLException {
        Map<ContentId, Map<ContentId, Integer>> ranks = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT skill_id, node_id, rank_value FROM mmorpg_life_skill_node_rank "
                        + "WHERE player_uuid = ?")) {
            select.setBytes(1, toBytes(playerId));
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    ranks.computeIfAbsent(ContentId.parse(rows.getString("skill_id")), key -> new HashMap<>())
                            .put(ContentId.parse(rows.getString("node_id")), rows.getInt("rank_value"));
                }
            }
        }
        return ranks;
    }

    static LifeSkillProfile readLifeSkills(Connection connection, UUID playerId)
            throws SQLException {
        Map<ContentId, Map<ContentId, Integer>> ranks = readNodeRanks(connection, playerId);
        Map<ContentId, LifeSkillSnapshot> skills = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT skill_id, level, total_xp, unspent_points, tree_revision, updated_at "
                        + "FROM mmorpg_life_skill_progress WHERE player_uuid = ?")) {
            select.setBytes(1, toBytes(playerId));
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    ContentId skillId = ContentId.parse(rows.getString("skill_id"));
                    LifeSkillProgress progress = new LifeSkillProgress(
                            skillId, rows.getInt("level"), rows.getLong("total_xp"),
                            rows.getInt("unspent_points"), rows.getLong("tree_revision"),
                            rows.getTimestamp("updated_at").toInstant());
                    skills.put(skillId, new LifeSkillSnapshot(
                            progress, ranks.getOrDefault(skillId, Map.of())));
                }
            }
        }
        return new LifeSkillProfile(playerId, skills, Instant.now());
    }

    static void lockPlayer(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement(
                "SELECT player_uuid FROM mmorpg_player_profiles "
                        + "WHERE player_uuid = ? FOR UPDATE")) {
            lock.setBytes(1, toBytes(playerId));
            try (ResultSet row = lock.executeQuery()) {
                if (!row.next()) {
                    throw new MMOException(ErrorCode.PROFILE_LOAD_FAILED,
                            "player profile is not loaded in storage " + playerId);
                }
            }
        }
    }

    private static String encodeSettings(Map<String, String> settings) throws SQLException {
        try {
            return OBJECT_MAPPER.writeValueAsString(settings);
        } catch (JsonProcessingException exception) {
            throw new SQLException("could not encode player settings", exception);
        }
    }

    private static Map<String, String> decodeSettings(String json) throws SQLException {
        try {
            return OBJECT_MAPPER.readValue(json, SETTINGS_TYPE);
        } catch (JsonProcessingException exception) {
            throw new SQLException("could not decode player settings", exception);
        }
    }

    private static Optional<ContentId> optionalContentId(String raw) {
        return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(ContentId.parse(raw));
    }

    static byte[] toBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}
