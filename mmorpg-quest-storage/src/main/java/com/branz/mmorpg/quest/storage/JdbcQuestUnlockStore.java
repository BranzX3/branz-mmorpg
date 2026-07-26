package com.branz.mmorpg.quest.storage;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.quest.api.QuestUnlockStore;
import com.branz.mmorpg.storage.DatabaseManager;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.UUID;

public final class JdbcQuestUnlockStore implements QuestUnlockStore {
    private final DatabaseManager database;

    public JdbcQuestUnlockStore(DatabaseManager database) {
        this.database = java.util.Objects.requireNonNull(database, "database");
    }

    @Override public boolean unlocked(UUID playerId, ContentId contentId) {
        try {
            return database.inTransaction(connection -> {
                try (var statement = connection.prepareStatement(
                        "SELECT 1 FROM quest_content_unlock "
                                + "WHERE player_uuid = ? AND content_id = ?")) {
                    statement.setBytes(1, bytes(playerId));
                    statement.setString(2, contentId.toString());
                    try (var row = statement.executeQuery()) {
                        return row.next();
                    }
                }
            });
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to read content unlock", failure);
        }
    }

    @Override public boolean unlock(
            UUID playerId, ContentId contentId, String operationId) {
        try {
            return database.inTransaction(connection -> {
                try (var statement = connection.prepareStatement(
                        "INSERT IGNORE INTO quest_content_unlock "
                                + "(player_uuid, content_id, operation_id) VALUES (?, ?, ?)")) {
                    statement.setBytes(1, bytes(playerId));
                    statement.setString(2, contentId.toString());
                    statement.setString(3, operationId);
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to persist content unlock", failure);
        }
    }

    private static byte[] bytes(UUID value) {
        return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits()).array();
    }
}
