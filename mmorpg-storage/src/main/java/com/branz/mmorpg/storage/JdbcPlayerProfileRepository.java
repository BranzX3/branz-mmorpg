package com.branz.mmorpg.storage;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.lifeskill.LifeSkillProfile;
import com.branz.mmorpg.api.lifeskill.LifeSkillProgress;
import com.branz.mmorpg.api.lifeskill.LifeSkillSnapshot;
import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.player.PlayerProfileRepository;
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

/**
 * JDBC {@link PlayerProfileRepository}.
 *
 * <p>UUIDs are stored as BINARY(16) to keep the primary key narrow; these tables
 * are read on every login.
 */
public final class JdbcPlayerProfileRepository implements PlayerProfileRepository {

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
                        "INSERT IGNORE INTO mmorpg_player_profile "
                                + "(player_uuid, last_known_name, schema_version, created_at, last_seen_at) "
                                + "VALUES (?, ?, ?, ?, ?)")) {
                    insert.setBytes(1, toBytes(playerId));
                    insert.setString(2, currentName == null ? "" : currentName);
                    insert.setInt(3, PlayerProfile.CURRENT_SCHEMA_VERSION);
                    insert.setTimestamp(4, Timestamp.from(now));
                    insert.setTimestamp(5, Timestamp.from(now));
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
            return databaseManager.inTransaction(connection -> {
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
                                    skillId,
                                    rows.getInt("level"),
                                    rows.getLong("total_xp"),
                                    rows.getInt("unspent_points"),
                                    rows.getLong("tree_revision"),
                                    rows.getTimestamp("updated_at").toInstant());
                            skills.put(skillId, new LifeSkillSnapshot(
                                    progress, ranks.getOrDefault(skillId, Map.of())));
                        }
                    }
                }
                return new LifeSkillProfile(playerId, skills, Instant.now());
            });
        } catch (SQLException exception) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE,
                    "failed to load life skills for " + playerId, exception);
        }
    }

    @Override
    public void saveProfile(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        try {
            databaseManager.inTransaction(connection -> {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE mmorpg_player_profile SET last_known_name = ?, schema_version = ?, "
                                + "last_seen_at = ?, selected_loadout_id = ?, respawn_point_id = ? "
                                + "WHERE player_uuid = ?")) {
                    update.setString(1, profile.lastKnownName());
                    update.setInt(2, profile.schemaVersion());
                    update.setTimestamp(3, Timestamp.from(profile.lastSeenAt()));
                    update.setString(4, profile.selectedLoadoutId().map(ContentId::toString).orElse(null));
                    update.setString(5, profile.respawnPointId().map(ContentId::toString).orElse(null));
                    update.setBytes(6, toBytes(profile.playerId()));
                    if (update.executeUpdate() == 0) {
                        throw new MMOException(ErrorCode.STORAGE_FAILURE,
                                "no profile row for " + profile.playerId() + "; refusing to create one "
                                        + "during save so a failed load cannot become a blank profile");
                    }
                }
                writeSettings(connection, profile);
                return null;
            });
        } catch (SQLException exception) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE,
                    "failed to save profile " + profile.playerId(), exception);
        }
    }

    private static PlayerProfile readProfile(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT last_known_name, schema_version, created_at, last_seen_at, "
                        + "selected_loadout_id, respawn_point_id "
                        + "FROM mmorpg_player_profile WHERE player_uuid = ?")) {
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
                        optionalContentId(rows.getString("selected_loadout_id")),
                        optionalContentId(rows.getString("respawn_point_id")),
                        readSettings(connection, playerId));
            }
        }
    }

    private static Map<String, String> readSettings(Connection connection, UUID playerId) throws SQLException {
        Map<String, String> settings = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT setting_key, setting_value FROM mmorpg_player_setting WHERE player_uuid = ?")) {
            select.setBytes(1, toBytes(playerId));
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    settings.put(rows.getString("setting_key"), rows.getString("setting_value"));
                }
            }
        }
        return settings;
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

    private static void writeSettings(Connection connection, PlayerProfile profile) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM mmorpg_player_setting WHERE player_uuid = ?")) {
            delete.setBytes(1, toBytes(profile.playerId()));
            delete.executeUpdate();
        }
        if (profile.settings().isEmpty()) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO mmorpg_player_setting (player_uuid, setting_key, setting_value) "
                        + "VALUES (?, ?, ?)")) {
            for (Map.Entry<String, String> entry : profile.settings().entrySet()) {
                insert.setBytes(1, toBytes(profile.playerId()));
                insert.setString(2, entry.getKey());
                insert.setString(3, entry.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static Optional<ContentId> optionalContentId(String raw) {
        return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(ContentId.parse(raw));
    }

    private static byte[] toBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}
