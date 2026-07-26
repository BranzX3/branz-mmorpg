package com.branz.mmorpg.quest.storage;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.quest.api.AccessibilitySettings;
import com.branz.mmorpg.quest.api.AccessibilitySettingsStore;
import com.branz.mmorpg.quest.api.CutsceneSession;
import com.branz.mmorpg.quest.api.CutsceneSessionStore;
import com.branz.mmorpg.quest.api.DialogueHistoryEntry;
import com.branz.mmorpg.quest.api.DialogueHistoryStore;
import com.branz.mmorpg.quest.api.DialogueSession;
import com.branz.mmorpg.quest.api.DialogueSessionStore;
import com.branz.mmorpg.storage.DatabaseManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcQuestSessionStore implements DialogueSessionStore,
        CutsceneSessionStore, DialogueHistoryStore, AccessibilitySettingsStore {
    private final DatabaseManager database;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public JdbcQuestSessionStore(DatabaseManager database) {
        this.database = java.util.Objects.requireNonNull(database, "database");
    }

    @Override public DialogueSession save(DialogueSession session) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO dialogue_session (session_uuid, player_uuid, dialogue_id, "
                                + "session_state, payload_json, updated_at) VALUES (?, ?, ?, ?, ?, ?) "
                                + "ON DUPLICATE KEY UPDATE session_state = VALUES(session_state), "
                                + "payload_json = VALUES(payload_json), updated_at = VALUES(updated_at)")) {
                    statement.setBytes(1, bytes(session.sessionId()));
                    statement.setBytes(2, bytes(session.playerId()));
                    statement.setString(3, session.dialogueId().toString());
                    statement.setString(4, session.state().name());
                    statement.setString(5, json(session));
                    statement.setTimestamp(6, Timestamp.from(session.lastActiveAt()));
                    statement.executeUpdate();
                }
                boolean live = session.state() == DialogueSession.State.ACTIVE
                        || session.state() == DialogueSession.State.PAUSED;
                if (live) {
                    try (PreparedStatement lock = connection.prepareStatement(
                            "INSERT INTO dialogue_player_lock (player_uuid, session_uuid) "
                                    + "VALUES (?, ?) ON DUPLICATE KEY UPDATE "
                                    + "session_uuid = IF(session_uuid = VALUES(session_uuid), "
                                    + "session_uuid, NULL)")) {
                        lock.setBytes(1, bytes(session.playerId()));
                        lock.setBytes(2, bytes(session.sessionId()));
                        lock.executeUpdate();
                    }
                } else {
                    removeDialogueLock(connection, session.sessionId());
                }
                return session;
            });
        } catch (SQLException failure) {
            throw storage("failed to save dialogue session", failure);
        }
    }

    @Override public Optional<DialogueSession> find(UUID sessionId) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT payload_json FROM dialogue_session WHERE session_uuid = ?")) {
                    statement.setBytes(1, bytes(sessionId));
                    try (ResultSet row = statement.executeQuery()) {
                        return row.next() ? Optional.of(
                                json(row.getString(1), DialogueSession.class)) : Optional.empty();
                    }
                }
            });
        } catch (SQLException failure) {
            throw storage("failed to read dialogue session", failure);
        }
    }

    @Override public Collection<DialogueSession> recoverable() {
        try {
            return database.inTransaction(connection -> {
                ArrayList<DialogueSession> result = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT payload_json FROM dialogue_session "
                                + "WHERE session_state IN ('ACTIVE','PAUSED')");
                     ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(
                            json(rows.getString(1), DialogueSession.class));
                }
                return List.copyOf(result);
            });
        } catch (SQLException failure) {
            throw storage("failed to recover dialogue sessions", failure);
        }
    }

    @Override public boolean remove(UUID sessionId) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM dialogue_session WHERE session_uuid = ?")) {
                    statement.setBytes(1, bytes(sessionId));
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException failure) {
            throw storage("failed to remove dialogue session", failure);
        }
    }

    @Override public CutsceneSession saveCutscene(CutsceneSession session) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO cutscene_session "
                                + "(session_uuid, cutscene_id, session_state, payload_json, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                                + "session_state = VALUES(session_state), "
                                + "payload_json = VALUES(payload_json), "
                                + "updated_at = VALUES(updated_at)")) {
                    statement.setBytes(1, bytes(session.sessionId()));
                    statement.setString(2, session.cutsceneId().toString());
                    statement.setString(3, session.state().name());
                    statement.setString(4, json(session));
                    statement.setTimestamp(5, Timestamp.from(session.updatedAt()));
                    statement.executeUpdate();
                }
                return session;
            });
        } catch (SQLException failure) {
            throw storage("failed to save cutscene session", failure);
        }
    }

    @Override public Optional<CutsceneSession> findCutscene(UUID sessionId) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT payload_json FROM cutscene_session WHERE session_uuid = ?")) {
                    statement.setBytes(1, bytes(sessionId));
                    try (ResultSet row = statement.executeQuery()) {
                        return row.next() ? Optional.of(
                                json(row.getString(1), CutsceneSession.class)) : Optional.empty();
                    }
                }
            });
        } catch (SQLException failure) {
            throw storage("failed to read cutscene session", failure);
        }
    }

    @Override public Collection<CutsceneSession> recoverableCutscenes() {
        try {
            return database.inTransaction(connection -> {
                ArrayList<CutsceneSession> result = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT payload_json FROM cutscene_session "
                                + "WHERE session_state <> 'COMPLETE'");
                     ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(
                            json(rows.getString(1), CutsceneSession.class));
                }
                return List.copyOf(result);
            });
        } catch (SQLException failure) {
            throw storage("failed to recover cutscene sessions", failure);
        }
    }

    @Override public boolean removeCutscene(UUID sessionId) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM cutscene_session WHERE session_uuid = ?")) {
                    statement.setBytes(1, bytes(sessionId));
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException failure) {
            throw storage("failed to remove cutscene session", failure);
        }
    }

    @Override public void append(DialogueHistoryEntry entry) {
        try {
            database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT IGNORE INTO dialogue_history "
                                + "(player_uuid, dialogue_id, session_uuid, sequence_number, "
                                + "node_id, speaker_key, text_key, choice_id, recorded_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    statement.setBytes(1, bytes(entry.playerId()));
                    statement.setString(2, entry.dialogueId().toString());
                    statement.setBytes(3, bytes(entry.sessionId()));
                    statement.setLong(4, entry.sequence());
                    statement.setString(5, entry.nodeId());
                    statement.setString(6, entry.speakerKey());
                    statement.setString(7, entry.textKey());
                    statement.setString(8, entry.choiceId() == null ? "" : entry.choiceId());
                    statement.setTimestamp(9, Timestamp.from(entry.recordedAt()));
                    statement.executeUpdate();
                }
                return null;
            });
        } catch (SQLException failure) {
            throw storage("failed to append dialogue history", failure);
        }
    }

    @Override public List<DialogueHistoryEntry> read(
            UUID playerId, ContentId dialogueId, int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("invalid history limit");
        try {
            return database.inTransaction(connection -> {
                ArrayList<DialogueHistoryEntry> result = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT session_uuid, sequence_number, node_id, speaker_key, text_key, "
                                + "choice_id, recorded_at FROM dialogue_history "
                                + "WHERE player_uuid = ? AND dialogue_id = ? "
                                + "ORDER BY recorded_at DESC LIMIT ?")) {
                    statement.setBytes(1, bytes(playerId));
                    statement.setString(2, dialogueId.toString());
                    statement.setInt(3, limit);
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) result.add(new DialogueHistoryEntry(
                                playerId, dialogueId, uuid(rows.getBytes(1)), rows.getLong(2),
                                rows.getString(3), rows.getString(4), rows.getString(5),
                                rows.getString(6), rows.getTimestamp(7).toInstant()));
                    }
                }
                return List.copyOf(result);
            });
        } catch (SQLException failure) {
            throw storage("failed to read dialogue history", failure);
        }
    }

    @Override public AccessibilitySettings load(UUID playerId) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT dialogue_mode, text_speed, skip_previously_read, "
                                + "portrait_intensity, vfx_intensity, sound_alternatives "
                                + "FROM quest_accessibility WHERE player_uuid = ?")) {
                    statement.setBytes(1, bytes(playerId));
                    try (ResultSet row = statement.executeQuery()) {
                        if (!row.next()) return AccessibilitySettings.defaults(playerId);
                        return new AccessibilitySettings(playerId,
                                AccessibilitySettings.DialogueMode.valueOf(row.getString(1)),
                                row.getDouble(2), row.getBoolean(3),
                                AccessibilitySettings.Intensity.valueOf(row.getString(4)),
                                AccessibilitySettings.Intensity.valueOf(row.getString(5)),
                                row.getBoolean(6));
                    }
                }
            });
        } catch (SQLException failure) {
            throw storage("failed to load accessibility settings", failure);
        }
    }

    @Override public AccessibilitySettings save(AccessibilitySettings settings) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO quest_accessibility (player_uuid, dialogue_mode, text_speed, "
                                + "skip_previously_read, portrait_intensity, vfx_intensity, "
                                + "sound_alternatives) VALUES (?, ?, ?, ?, ?, ?, ?) "
                                + "ON DUPLICATE KEY UPDATE dialogue_mode = VALUES(dialogue_mode), "
                                + "text_speed = VALUES(text_speed), "
                                + "skip_previously_read = VALUES(skip_previously_read), "
                                + "portrait_intensity = VALUES(portrait_intensity), "
                                + "vfx_intensity = VALUES(vfx_intensity), "
                                + "sound_alternatives = VALUES(sound_alternatives)")) {
                    statement.setBytes(1, bytes(settings.playerId()));
                    statement.setString(2, settings.dialogueMode().name());
                    statement.setDouble(3, settings.textSpeed());
                    statement.setBoolean(4, settings.skipPreviouslyRead());
                    statement.setString(5, settings.portraitIntensity().name());
                    statement.setString(6, settings.vfxIntensity().name());
                    statement.setBoolean(7, settings.soundAlternatives());
                    statement.executeUpdate();
                }
                return settings;
            });
        } catch (SQLException failure) {
            throw storage("failed to save accessibility settings", failure);
        }
    }

    private static void removeDialogueLock(
            java.sql.Connection connection, UUID sessionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM dialogue_player_lock WHERE session_uuid = ?")) {
            statement.setBytes(1, bytes(sessionId));
            statement.executeUpdate();
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE, "session JSON encode failed", failure);
        }
    }
    private <T> T json(String value, Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException failure) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE, "session JSON decode failed", failure);
        }
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
