package com.branz.mmorpg.storage;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.item.PendingSlotItem;
import com.branz.mmorpg.api.item.PendingSlotItemRepository;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcPendingSlotItemRepository implements PendingSlotItemRepository {
    private final DatabaseManager database;

    public JdbcPendingSlotItemRepository(DatabaseManager database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override public Optional<PendingSlotItem> find(UUID playerId) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT delivery_uuid, payload, payload_hash, created_at "
                                + "FROM mmorpg_reserved_slot_pending_item WHERE player_uuid = ?")) {
                    select.setBytes(1, bytes(playerId));
                    try (ResultSet row = select.executeQuery()) {
                        if (!row.next()) return Optional.empty();
                        return Optional.of(new PendingSlotItem(playerId,
                                uuid(row.getBytes("delivery_uuid")), row.getBytes("payload"),
                                row.getString("payload_hash"),
                                row.getTimestamp("created_at").toInstant()));
                    }
                }
            });
        } catch (SQLException exception) {
            throw failure("failed to load reserved-slot pending item", exception);
        }
    }

    @Override public PendingSlotItem store(PendingSlotItem item) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT IGNORE INTO mmorpg_reserved_slot_pending_item "
                                + "(player_uuid, delivery_uuid, payload, payload_hash, created_at) "
                                + "VALUES (?, ?, ?, ?, ?)")) {
                    insert.setBytes(1, bytes(item.playerId()));
                    insert.setBytes(2, bytes(item.deliveryId()));
                    insert.setBytes(3, item.payload());
                    insert.setString(4, item.payloadHash());
                    insert.setTimestamp(5, Timestamp.from(item.createdAt()));
                    insert.executeUpdate();
                }
                PendingSlotItem stored = find(connection, item.playerId());
                if (!stored.deliveryId().equals(item.deliveryId())
                        || !stored.payloadHash().equals(item.payloadHash())
                        || !Arrays.equals(stored.payload(), item.payload())) {
                    throw new MMOException(ErrorCode.STORAGE_FAILURE,
                            "reserved slot already contains a different pending item");
                }
                return stored;
            });
        } catch (SQLException exception) {
            throw failure("failed to store reserved-slot pending item", exception);
        }
    }

    private static PendingSlotItem find(java.sql.Connection connection, UUID playerId)
            throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT delivery_uuid, payload, payload_hash, created_at "
                        + "FROM mmorpg_reserved_slot_pending_item WHERE player_uuid = ?")) {
            select.setBytes(1, bytes(playerId));
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) throw new SQLException("pending slot row disappeared");
                return new PendingSlotItem(playerId, uuid(row.getBytes("delivery_uuid")),
                        row.getBytes("payload"), row.getString("payload_hash"),
                        row.getTimestamp("created_at").toInstant());
            }
        }
    }

    private static byte[] bytes(UUID value) {
        return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits()).array();
    }

    private static UUID uuid(byte[] value) {
        ByteBuffer bytes = ByteBuffer.wrap(value);
        return new UUID(bytes.getLong(), bytes.getLong());
    }

    private static MMOException failure(String message, Exception exception) {
        return new MMOException(ErrorCode.STORAGE_FAILURE, message, exception);
    }
}
