package com.branz.mmorpg.storage;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.item.EquipmentSlot;
import com.branz.mmorpg.api.item.InventoryMutationCommit;
import com.branz.mmorpg.api.item.InventoryRepository;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.item.ItemCategory;
import com.branz.mmorpg.api.item.ItemInstance;
import com.branz.mmorpg.api.operation.OperationId;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public final class JdbcInventoryRepository implements InventoryRepository {
    private static final int DEFAULT_CAPACITY = 36;
    private final DatabaseManager database;

    public JdbcInventoryRepository(DatabaseManager database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public InventorySnapshot load(UUID playerId) {
        try {
            return database.inTransaction(connection -> {
                ensureInventory(connection, playerId);
                return loadSnapshot(connection, playerId, false);
            });
        } catch (SQLException failure) {
            throw storage("failed to load inventory for " + playerId, failure);
        }
    }

    @Override
    public InventoryMutationCommit mutate(
            UUID playerId, OperationId operationId, long delivered, long overflowed,
            UnaryOperator<InventorySnapshot> mutation) {
        if (!playerId.equals(operationId.playerUuid())) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "operation player does not match inventory owner");
        }
        try {
            return database.inTransaction(connection -> {
                ensureInventory(connection, playerId);
                InventorySnapshot before = loadSnapshot(connection, playerId, true);
                if (!claim(connection, playerId, operationId)) {
                    return new InventoryMutationCommit(false, before, before, 0, 0);
                }
                InventorySnapshot after = Objects.requireNonNull(mutation.apply(before),
                        "inventory mutation returned null");
                if (!playerId.equals(after.playerId())) {
                    throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                            "inventory mutation changed its owner");
                }
                replaceSnapshot(connection, after);
                audit(connection, playerId, operationId);
                return new InventoryMutationCommit(
                        true, before, after, delivered, overflowed);
            });
        } catch (SQLException failure) {
            throw storage("failed inventory operation " + operationId, failure);
        }
    }

    static void ensureInventory(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT IGNORE INTO mmorpg_inventory (player_uuid, slot_capacity) VALUES (?, ?)")) {
            insert.setBytes(1, bytes(playerId));
            insert.setInt(2, DEFAULT_CAPACITY);
            insert.executeUpdate();
        }
    }

    static InventorySnapshot loadSnapshot(Connection connection, UUID playerId, boolean lock)
            throws SQLException {
        int capacity;
        Instant updated;
        String sql = "SELECT slot_capacity, updated_at FROM mmorpg_inventory "
                + "WHERE player_uuid = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            select.setBytes(1, bytes(playerId));
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) throw new SQLException("inventory row disappeared");
                capacity = row.getInt(1);
                updated = row.getTimestamp(2).toInstant();
            }
        }
        Map<ContentId, Long> materials = new LinkedHashMap<>();
        Map<ContentId, Long> pendingMaterials = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT definition_id, location, quantity FROM mmorpg_inventory_material "
                        + "WHERE player_uuid = ?")) {
            select.setBytes(1, bytes(playerId));
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    Map<ContentId, Long> target = "PENDING".equals(rows.getString(2))
                            ? pendingMaterials : materials;
                    target.put(ContentId.parse(rows.getString(1)), rows.getLong(3));
                }
            }
        }
        Map<UUID, ItemInstance> items = new LinkedHashMap<>();
        Map<UUID, ItemInstance> pendingItems = new LinkedHashMap<>();
        Map<EquipmentSlot, UUID> equipped = new EnumMap<>(EquipmentSlot.class);
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT item_uuid, definition_id, category, quality_seed, bound_owner_uuid, "
                        + "durability, created_source, schema_version, location, equipped_slot, "
                        + "created_at FROM mmorpg_item_instance WHERE owner_uuid = ?")) {
            select.setBytes(1, bytes(playerId));
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    UUID itemId = uuid(rows.getBytes("item_uuid"));
                    byte[] boundBytes = rows.getBytes("bound_owner_uuid");
                    ItemInstance item = new ItemInstance(itemId,
                            ContentId.parse(rows.getString("definition_id")),
                            ItemCategory.valueOf(rows.getString("category")),
                            rows.getLong("quality_seed"),
                            boundBytes == null ? Optional.empty() : Optional.of(uuid(boundBytes)),
                            rows.getInt("durability"), rows.getString("created_source"),
                            rows.getInt("schema_version"),
                            rows.getTimestamp("created_at").toInstant());
                    if ("PENDING".equals(rows.getString("location"))) {
                        pendingItems.put(itemId, item);
                    } else {
                        items.put(itemId, item);
                        String slot = rows.getString("equipped_slot");
                        if (slot != null) equipped.put(EquipmentSlot.valueOf(slot), itemId);
                    }
                }
            }
        }
        return new InventorySnapshot(playerId, capacity, materials, items, equipped,
                pendingMaterials, pendingItems, updated);
    }

    static void replaceSnapshot(Connection connection, InventorySnapshot snapshot)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE mmorpg_inventory SET slot_capacity = ?, updated_at = ? "
                        + "WHERE player_uuid = ?")) {
            update.setInt(1, snapshot.slotCapacity());
            update.setTimestamp(2, Timestamp.from(snapshot.updatedAt()));
            update.setBytes(3, bytes(snapshot.playerId()));
            update.executeUpdate();
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM mmorpg_inventory_material WHERE player_uuid = ?")) {
            delete.setBytes(1, bytes(snapshot.playerId()));
            delete.executeUpdate();
        }
        insertMaterials(connection, snapshot.playerId(), snapshot.materials(), "INVENTORY");
        insertMaterials(connection, snapshot.playerId(), snapshot.pendingMaterials(), "PENDING");
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM mmorpg_item_instance WHERE owner_uuid = ?")) {
            delete.setBytes(1, bytes(snapshot.playerId()));
            delete.executeUpdate();
        }
        Map<UUID, EquipmentSlot> slots = new LinkedHashMap<>();
        snapshot.equipped().forEach((slot, id) -> slots.put(id, slot));
        insertItems(connection, snapshot.playerId(), snapshot.items(), "INVENTORY", slots);
        insertItems(connection, snapshot.playerId(), snapshot.pendingItems(), "PENDING", Map.of());
    }

    private static void insertMaterials(Connection connection, UUID playerId,
                                        Map<ContentId, Long> values, String location)
            throws SQLException {
        if (values.isEmpty()) return;
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO mmorpg_inventory_material "
                        + "(player_uuid, definition_id, location, quantity) VALUES (?, ?, ?, ?)")) {
            for (var entry : values.entrySet()) {
                insert.setBytes(1, bytes(playerId));
                insert.setString(2, entry.getKey().toString());
                insert.setString(3, location);
                insert.setLong(4, entry.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void insertItems(Connection connection, UUID playerId,
                                    Map<UUID, ItemInstance> values, String location,
                                    Map<UUID, EquipmentSlot> slots) throws SQLException {
        if (values.isEmpty()) return;
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO mmorpg_item_instance (item_uuid, owner_uuid, definition_id, "
                        + "category, quality_seed, bound_owner_uuid, durability, created_source, "
                        + "schema_version, location, equipped_slot, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (ItemInstance item : values.values()) {
                insert.setBytes(1, bytes(item.instanceId()));
                insert.setBytes(2, bytes(playerId));
                insert.setString(3, item.definitionId().toString());
                insert.setString(4, item.category().name());
                insert.setLong(5, item.qualitySeed());
                if (item.boundOwner().isPresent()) {
                    insert.setBytes(6, bytes(item.boundOwner().get()));
                } else {
                    insert.setNull(6, java.sql.Types.BINARY);
                }
                insert.setInt(7, item.durability());
                insert.setString(8, item.createdSource());
                insert.setInt(9, item.schemaVersion());
                insert.setString(10, location);
                EquipmentSlot slot = slots.get(item.instanceId());
                if (slot == null) insert.setNull(11, java.sql.Types.VARCHAR);
                else insert.setString(11, slot.name());
                insert.setTimestamp(12, Timestamp.from(item.createdAt()));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static boolean claim(Connection connection, UUID playerId, OperationId operation)
            throws SQLException {
        try (PreparedStatement claim = connection.prepareStatement(
                "INSERT IGNORE INTO mmorpg_processed_operation "
                        + "(operation_id, player_uuid, subsystem) VALUES (?, ?, ?)")) {
            claim.setString(1, operation.value());
            claim.setBytes(2, bytes(playerId));
            claim.setString(3, operation.subsystem());
            return claim.executeUpdate() == 1;
        }
    }

    private static void audit(Connection connection, UUID playerId, OperationId operation)
            throws SQLException {
        try (PreparedStatement audit = connection.prepareStatement(
                "INSERT INTO mmorpg_audit_log (actor_uuid, action, subject) VALUES (?, ?, ?)")) {
            audit.setBytes(1, bytes(playerId));
            audit.setString(2, "inventory_mutation");
            audit.setString(3, operation.value());
            audit.executeUpdate();
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

    private static MMOException storage(String message, Throwable cause) {
        return new MMOException(ErrorCode.STORAGE_FAILURE, message, cause);
    }
}
