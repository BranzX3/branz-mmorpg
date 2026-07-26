package com.branz.mmorpg.storage.player;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.player.PlayerProfileStore;
import com.branz.mmorpg.storage.DatabaseManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public final class MySqlPlayerProfileStore implements PlayerProfileStore {
    private static final TypeReference<Map<String, String>> SETTINGS_TYPE = new TypeReference<>() {};

    private final DatabaseManager database;
    private final Executor executor;
    private final ObjectMapper objectMapper;

    public MySqlPlayerProfileStore(DatabaseManager database, Executor executor) {
        this(database, executor, new ObjectMapper());
    }

    MySqlPlayerProfileStore(DatabaseManager database, Executor executor, ObjectMapper objectMapper) {
        this.database = Objects.requireNonNull(database, "database");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public CompletionStage<PlayerProfile> loadOrCreate(UUID playerId, String lastKnownName, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lastKnownName, "lastKnownName");
        Objects.requireNonNull(now, "now");
        return supplyAsync(() -> database.inTransaction(connection -> {
            insertIfAbsent(connection, playerId, lastKnownName, now);
            return select(connection, playerId).seenAs(lastKnownName, now);
        }));
    }

    @Override
    public CompletionStage<PlayerProfile> save(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return supplyAsync(() -> database.inTransaction(connection -> {
            int changed = update(connection, profile);
            if (changed != 1) {
                throw new PlayerProfileConflictException(profile.playerId(), profile.revision());
            }
            return select(connection, profile.playerId());
        }));
    }

    private void insertIfAbsent(Connection connection, UUID playerId, String name, Instant now)
            throws SQLException {
        String sql = """
                INSERT IGNORE INTO mmorpg_player_profiles (
                    player_uuid, last_known_name, schema_version, created_at,
                    last_seen_at, settings_json, revision
                ) VALUES (UUID_TO_BIN(?), ?, ?, ?, ?, CAST(? AS JSON), 0)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, name);
            statement.setInt(3, PlayerProfile.CURRENT_SCHEMA_VERSION);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setString(6, "{}");
            statement.executeUpdate();
        }
    }

    private int update(Connection connection, PlayerProfile profile) throws SQLException {
        String sql = """
                UPDATE mmorpg_player_profiles
                SET last_known_name = ?, schema_version = ?, last_seen_at = ?,
                    class_id = ?, selected_loadout_id = ?, respawn_point_id = ?,
                    settings_json = CAST(? AS JSON), revision = revision + 1
                WHERE player_uuid = UUID_TO_BIN(?) AND revision = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.lastKnownName());
            statement.setInt(2, profile.schemaVersion());
            statement.setTimestamp(3, Timestamp.from(profile.lastSeenAt()));
            setContentId(statement, 4, profile.classId());
            setContentId(statement, 5, profile.selectedLoadoutId());
            setContentId(statement, 6, profile.respawnPointId());
            statement.setString(7, encodeSettings(profile.settings()));
            statement.setString(8, profile.playerId().toString());
            statement.setLong(9, profile.revision());
            return statement.executeUpdate();
        }
    }

    private PlayerProfile select(Connection connection, UUID playerId) throws SQLException {
        String sql = """
                SELECT BIN_TO_UUID(player_uuid) AS player_uuid, last_known_name,
                       schema_version, created_at, last_seen_at, class_id,
                       selected_loadout_id, respawn_point_id, settings_json, revision
                FROM mmorpg_player_profiles
                WHERE player_uuid = UUID_TO_BIN(?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Player profile not found: " + playerId);
                }
                return new PlayerProfile(
                        UUID.fromString(result.getString("player_uuid")),
                        result.getString("last_known_name"),
                        result.getInt("schema_version"),
                        result.getTimestamp("created_at").toInstant(),
                        result.getTimestamp("last_seen_at").toInstant(),
                        contentId(result.getString("class_id")),
                        contentId(result.getString("selected_loadout_id")),
                        contentId(result.getString("respawn_point_id")),
                        decodeSettings(result.getString("settings_json")),
                        result.getLong("revision"));
            }
        }
    }

    private CompletionStage<PlayerProfile> supplyAsync(SqlSupplier<PlayerProfile> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    private String encodeSettings(Map<String, String> settings) throws SQLException {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Could not encode player settings", exception);
        }
    }

    private Map<String, String> decodeSettings(String json) throws SQLException {
        try {
            return objectMapper.readValue(json, SETTINGS_TYPE);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Could not decode player settings", exception);
        }
    }

    private static Optional<ContentId> contentId(String value) {
        return value == null ? Optional.empty() : Optional.of(ContentId.parse(value));
    }

    private static void setContentId(
            PreparedStatement statement, int index, Optional<ContentId> contentId) throws SQLException {
        if (contentId.isPresent()) {
            statement.setString(index, contentId.orElseThrow().toString());
        } else {
            statement.setNull(index, java.sql.Types.VARCHAR);
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}
