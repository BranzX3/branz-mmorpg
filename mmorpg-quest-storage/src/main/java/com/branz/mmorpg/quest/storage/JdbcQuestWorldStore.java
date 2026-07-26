package com.branz.mmorpg.quest.storage;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.storage.DatabaseManager;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistent registry for named quest locations and interactive world blocks. */
public final class JdbcQuestWorldStore {
    private final DatabaseManager database;

    public JdbcQuestWorldStore(DatabaseManager database) {
        this.database = java.util.Objects.requireNonNull(database, "database");
    }

    public List<LocationRecord> locations() {
        try {
            return database.inTransaction(connection -> {
                List<LocationRecord> values = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT location_id, world_uuid, x, y, z, yaw, pitch "
                                + "FROM quest_location ORDER BY location_id");
                     ResultSet row = statement.executeQuery()) {
                    while (row.next()) {
                        values.add(new LocationRecord(row.getString(1), uuid(row.getBytes(2)),
                                row.getDouble(3), row.getDouble(4), row.getDouble(5),
                                row.getFloat(6), row.getFloat(7)));
                    }
                }
                return List.copyOf(values);
            });
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to read quest locations", failure);
        }
    }

    public List<WorldObjectRecord> worldObjects() {
        try {
            return database.inTransaction(connection -> {
                List<WorldObjectRecord> values = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT world_uuid, block_x, block_y, block_z, object_id "
                                + "FROM quest_world_object");
                     ResultSet row = statement.executeQuery()) {
                    while (row.next()) {
                        values.add(new WorldObjectRecord(uuid(row.getBytes(1)),
                                row.getInt(2), row.getInt(3), row.getInt(4),
                                ContentId.parse(row.getString(5))));
                    }
                }
                return List.copyOf(values);
            });
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to read quest world objects", failure);
        }
    }

    public void save(LocationRecord value) {
        try {
            database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO quest_location "
                                + "(location_id, world_uuid, x, y, z, yaw, pitch) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                                + "world_uuid=VALUES(world_uuid), x=VALUES(x), y=VALUES(y), "
                                + "z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)")) {
                    statement.setString(1, value.id());
                    statement.setBytes(2, bytes(value.worldId()));
                    statement.setDouble(3, value.x());
                    statement.setDouble(4, value.y());
                    statement.setDouble(5, value.z());
                    statement.setFloat(6, value.yaw());
                    statement.setFloat(7, value.pitch());
                    statement.executeUpdate();
                }
                return null;
            });
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to save quest location", failure);
        }
    }

    public boolean deleteLocation(String id) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM quest_location WHERE location_id = ?")) {
                    statement.setString(1, id);
                    return statement.executeUpdate() > 0;
                }
            });
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to delete quest location", failure);
        }
    }

    public void save(WorldObjectRecord value) {
        try {
            database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO quest_world_object "
                                + "(world_uuid, block_x, block_y, block_z, object_id) "
                                + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                                + "object_id=VALUES(object_id)")) {
                    statement.setBytes(1, bytes(value.worldId()));
                    statement.setInt(2, value.x());
                    statement.setInt(3, value.y());
                    statement.setInt(4, value.z());
                    statement.setString(5, value.objectId().toString());
                    statement.executeUpdate();
                }
                return null;
            });
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to save quest world object", failure);
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

    public record LocationRecord(String id, UUID worldId, double x, double y,
                                 double z, float yaw, float pitch) {}
    public record WorldObjectRecord(UUID worldId, int x, int y, int z,
                                    ContentId objectId) {}
}
