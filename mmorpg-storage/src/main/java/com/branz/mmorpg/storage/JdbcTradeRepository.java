package com.branz.mmorpg.storage;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.social.TradeOffer;
import com.branz.mmorpg.api.social.TradeRepository;
import com.branz.mmorpg.api.social.TradeSnapshot;
import com.branz.mmorpg.api.social.TradeState;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class JdbcTradeRepository implements TradeRepository {
    private final DatabaseManager database;

    public JdbcTradeRepository(DatabaseManager database) {
        this.database = java.util.Objects.requireNonNull(database, "database");
    }

    @Override public TradeSnapshot create(TradeSnapshot trade) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO mmorpg_trade (trade_uuid, requester_uuid, recipient_uuid, "
                                + "trade_state, created_at, expires_at, trade_revision) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    statement.setBytes(1, bytes(trade.tradeId()));
                    statement.setBytes(2, bytes(trade.requesterId()));
                    statement.setBytes(3, bytes(trade.recipientId()));
                    statement.setString(4, trade.state().name());
                    statement.setTimestamp(5, Timestamp.from(trade.createdAt()));
                    statement.setTimestamp(6, Timestamp.from(trade.expiresAt()));
                    statement.setLong(7, trade.revision());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO mmorpg_trade_participant_lock "
                                + "(player_uuid, trade_uuid) VALUES (?, ?)")) {
                    for (UUID player : Set.of(trade.requesterId(), trade.recipientId())) {
                        statement.setBytes(1, bytes(player));
                        statement.setBytes(2, bytes(trade.tradeId()));
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                return trade;
            });
        } catch (SQLException failure) {
            throw storage("failed to create trade", failure);
        }
    }

    @Override public Optional<TradeSnapshot> find(UUID tradeId) {
        try {
            return database.inTransaction(connection -> read(connection, tradeId, false));
        } catch (SQLException failure) {
            throw storage("failed to read trade " + tradeId, failure);
        }
    }

    @Override public TradeSnapshot accept(UUID tradeId, UUID recipientId, Instant now) {
        try {
            return database.inTransaction(connection -> {
                TradeSnapshot trade = require(connection, tradeId);
                expireCheck(trade, now);
                if (trade.state() != TradeState.REQUESTED
                        || !trade.recipientId().equals(recipientId)) reject("trade cannot be accepted");
                updateState(connection, trade, TradeState.OPEN, now, false);
                return require(connection, tradeId);
            });
        } catch (SQLException failure) {
            throw storage("failed to accept trade " + tradeId, failure);
        }
    }

    @Override public TradeSnapshot replaceOffer(
            UUID tradeId, UUID playerId, TradeOffer offer, Instant now) {
        try {
            return database.inTransaction(connection -> {
                TradeSnapshot trade = require(connection, tradeId);
                expireCheck(trade, now);
                if (trade.state() != TradeState.OPEN) reject("trade is not open");
                trade.counterpart(playerId);
                JdbcInventoryRepository.ensureInventory(connection, playerId);
                lockInventory(connection, playerId);
                TradeOffer previous = trade.offers().getOrDefault(playerId, TradeOffer.empty());
                replaceMaterials(connection, tradeId, playerId, previous.materials(),
                        offer.materials());
                replaceItems(connection, tradeId, playerId, previous.itemIds(), offer.itemIds());
                try (PreparedStatement clear = connection.prepareStatement(
                        "DELETE FROM mmorpg_trade_confirmation WHERE trade_uuid = ?")) {
                    clear.setBytes(1, bytes(tradeId));
                    clear.executeUpdate();
                }
                bump(connection, trade, TradeState.OPEN);
                audit(connection, playerId, "trade_offer_replace", tradeId.toString());
                return require(connection, tradeId);
            });
        } catch (SQLException failure) {
            throw storage("failed to replace trade offer " + tradeId, failure);
        }
    }

    @Override public TradeSnapshot confirm(UUID tradeId, UUID playerId, Instant now) {
        try {
            return database.inTransaction(connection -> {
                TradeSnapshot trade = require(connection, tradeId);
                expireCheck(trade, now);
                if (trade.state() != TradeState.OPEN) reject("trade is not open");
                trade.counterpart(playerId);
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT IGNORE INTO mmorpg_trade_confirmation "
                                + "(trade_uuid, player_uuid) VALUES (?, ?)")) {
                    statement.setBytes(1, bytes(tradeId));
                    statement.setBytes(2, bytes(playerId));
                    statement.executeUpdate();
                }
                int confirmations;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM mmorpg_trade_confirmation WHERE trade_uuid = ?")) {
                    statement.setBytes(1, bytes(tradeId));
                    try (ResultSet row = statement.executeQuery()) {
                        row.next();
                        confirmations = row.getInt(1);
                    }
                }
                bump(connection, trade, confirmations == 2
                        ? TradeState.BOTH_CONFIRMED : TradeState.OPEN);
                return require(connection, tradeId);
            });
        } catch (SQLException failure) {
            throw storage("failed to confirm trade " + tradeId, failure);
        }
    }

    @Override public TradeSnapshot commit(UUID tradeId, Instant now) {
        try {
            return database.inTransaction(connection -> {
                TradeSnapshot trade = require(connection, tradeId);
                if (trade.state() == TradeState.COMPLETE) return trade;
                expireCheck(trade, now);
                if (trade.state() != TradeState.BOTH_CONFIRMED
                        && trade.state() != TradeState.COMMITTING) {
                    reject("trade is not confirmed");
                }
                lockInventories(connection, trade.requesterId(), trade.recipientId());
                updateRawState(connection, tradeId, TradeState.COMMITTING,
                        trade.revision() + 1);
                transferEscrow(connection, trade);
                clearEscrow(connection, tradeId);
                updateRawState(connection, tradeId, TradeState.COMPLETE,
                        trade.revision() + 2);
                releaseParticipantLocks(connection, tradeId);
                audit(connection, trade.requesterId(), "trade_commit", tradeId.toString());
                return require(connection, tradeId);
            });
        } catch (SQLException failure) {
            throw storage("failed to commit trade " + tradeId, failure);
        }
    }

    @Override public TradeSnapshot cancel(
            UUID tradeId, TradeState terminalState, Instant now) {
        if (terminalState != TradeState.CANCELLED && terminalState != TradeState.EXPIRED) {
            throw new IllegalArgumentException("invalid trade cancellation state");
        }
        try {
            return database.inTransaction(connection -> {
                TradeSnapshot trade = require(connection, tradeId);
                if (trade.state() == TradeState.CANCELLED
                        || trade.state() == TradeState.EXPIRED) return trade;
                if (trade.state() == TradeState.COMPLETE) reject("completed trade cannot cancel");
                lockInventories(connection, trade.requesterId(), trade.recipientId());
                returnEscrow(connection, tradeId);
                clearEscrow(connection, tradeId);
                updateRawState(connection, tradeId, terminalState, trade.revision() + 1);
                releaseParticipantLocks(connection, tradeId);
                audit(connection, trade.requesterId(), "trade_" + terminalState.name()
                        .toLowerCase(java.util.Locale.ROOT), tradeId.toString());
                return require(connection, tradeId);
            });
        } catch (SQLException failure) {
            throw storage("failed to cancel trade " + tradeId, failure);
        }
    }

    @Override public Collection<TradeSnapshot> recoverable(Instant now) {
        try {
            return database.inTransaction(connection -> {
                ArrayList<UUID> ids = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT trade_uuid FROM mmorpg_trade WHERE trade_state IN "
                                + "('REQUESTED','OPEN','BOTH_CONFIRMED','COMMITTING') "
                                + "ORDER BY created_at");
                     ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) ids.add(uuid(rows.getBytes(1)));
                }
                ArrayList<TradeSnapshot> result = new ArrayList<>();
                for (UUID id : ids) result.add(read(connection, id, false).orElseThrow());
                return java.util.List.copyOf(result);
            });
        } catch (SQLException failure) {
            throw storage("failed to recover trades", failure);
        }
    }

    private static Optional<TradeSnapshot> read(
            Connection connection, UUID tradeId, boolean lock) throws SQLException {
        Header header;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT requester_uuid, recipient_uuid, trade_state, created_at, expires_at, "
                        + "trade_revision FROM mmorpg_trade WHERE trade_uuid = ?"
                        + (lock ? " FOR UPDATE" : ""))) {
            statement.setBytes(1, bytes(tradeId));
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                header = new Header(uuid(row.getBytes(1)), uuid(row.getBytes(2)),
                        TradeState.valueOf(row.getString(3)),
                        row.getTimestamp(4).toInstant(), row.getTimestamp(5).toInstant(),
                        row.getLong(6));
            }
        }
        Map<UUID, Map<ContentId, Long>> materials = new HashMap<>();
        Map<UUID, Set<UUID>> items = new HashMap<>();
        for (UUID player : Set.of(header.requester(), header.recipient())) {
            materials.put(player, new LinkedHashMap<>());
            items.put(player, new HashSet<>());
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT owner_uuid, definition_id, quantity "
                        + "FROM mmorpg_trade_escrow_material WHERE trade_uuid = ?")) {
            statement.setBytes(1, bytes(tradeId));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) materials.get(uuid(rows.getBytes(1)))
                        .put(ContentId.parse(rows.getString(2)), rows.getLong(3));
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT original_owner_uuid, item_uuid FROM mmorpg_trade_escrow_item "
                        + "WHERE trade_uuid = ?")) {
            statement.setBytes(1, bytes(tradeId));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) items.get(uuid(rows.getBytes(1)))
                        .add(uuid(rows.getBytes(2)));
            }
        }
        HashSet<UUID> confirmations = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT player_uuid FROM mmorpg_trade_confirmation WHERE trade_uuid = ?")) {
            statement.setBytes(1, bytes(tradeId));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) confirmations.add(uuid(rows.getBytes(1)));
            }
        }
        Map<UUID, TradeOffer> offers = Map.of(
                header.requester(), new TradeOffer(
                        materials.get(header.requester()), items.get(header.requester())),
                header.recipient(), new TradeOffer(
                        materials.get(header.recipient()), items.get(header.recipient())));
        return Optional.of(new TradeSnapshot(tradeId, header.requester(), header.recipient(),
                header.state(), offers, confirmations, header.createdAt(),
                header.expiresAt(), header.revision()));
    }

    private static TradeSnapshot require(Connection connection, UUID tradeId) throws SQLException {
        return read(connection, tradeId, true).orElseThrow(
                () -> new MMOException(ErrorCode.INVALID_ARGUMENT, "unknown trade " + tradeId));
    }

    private static void replaceMaterials(
            Connection connection, UUID tradeId, UUID playerId,
            Map<ContentId, Long> previous, Map<ContentId, Long> next) throws SQLException {
        HashSet<ContentId> ids = new HashSet<>(previous.keySet());
        ids.addAll(next.keySet());
        for (ContentId id : ids) {
            long before = previous.getOrDefault(id, 0L);
            long after = next.getOrDefault(id, 0L);
            if (after > before) {
                deductMaterial(connection, playerId, id, after - before);
            } else if (before > after) {
                addPendingMaterial(connection, playerId, id, before - after);
            }
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM mmorpg_trade_escrow_material "
                        + "WHERE trade_uuid = ? AND owner_uuid = ?")) {
            delete.setBytes(1, bytes(tradeId));
            delete.setBytes(2, bytes(playerId));
            delete.executeUpdate();
        }
        if (next.isEmpty()) return;
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO mmorpg_trade_escrow_material "
                        + "(trade_uuid, owner_uuid, definition_id, quantity) VALUES (?, ?, ?, ?)")) {
            for (var entry : next.entrySet()) {
                insert.setBytes(1, bytes(tradeId));
                insert.setBytes(2, bytes(playerId));
                insert.setString(3, entry.getKey().toString());
                insert.setLong(4, entry.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void replaceItems(
            Connection connection, UUID tradeId, UUID playerId,
            Set<UUID> previous, Set<UUID> next) throws SQLException {
        for (UUID itemId : previous) {
            if (!next.contains(itemId)) {
                restoreEscrowItem(connection, tradeId, playerId, itemId);
            }
        }
        for (UUID itemId : next) {
            if (!previous.contains(itemId)) {
                escrowInventoryItem(connection, tradeId, playerId, itemId);
            }
        }
    }

    private static void deductMaterial(
            Connection connection, UUID playerId, ContentId id, long quantity) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE mmorpg_inventory_material SET quantity = quantity - ? "
                        + "WHERE player_uuid = ? AND definition_id = ? "
                        + "AND location = 'INVENTORY' AND quantity >= ?")) {
            update.setLong(1, quantity);
            update.setBytes(2, bytes(playerId));
            update.setString(3, id.toString());
            update.setLong(4, quantity);
            if (update.executeUpdate() != 1) reject("insufficient material " + id);
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM mmorpg_inventory_material WHERE player_uuid = ? "
                        + "AND definition_id = ? AND location = 'INVENTORY' AND quantity = 0")) {
            delete.setBytes(1, bytes(playerId));
            delete.setString(2, id.toString());
            delete.executeUpdate();
        }
    }

    private static void addPendingMaterial(
            Connection connection, UUID playerId, ContentId id, long quantity) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO mmorpg_inventory_material "
                        + "(player_uuid, definition_id, location, quantity) "
                        + "VALUES (?, ?, 'PENDING', ?) ON DUPLICATE KEY UPDATE "
                        + "quantity = quantity + VALUES(quantity)")) {
            insert.setBytes(1, bytes(playerId));
            insert.setString(2, id.toString());
            insert.setLong(3, quantity);
            insert.executeUpdate();
        }
    }

    private static void escrowInventoryItem(
            Connection connection, UUID tradeId, UUID owner, UUID itemId) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO mmorpg_trade_escrow_item "
                        + "(trade_uuid, original_owner_uuid, item_uuid, definition_id, category, "
                        + "quality_seed, bound_owner_uuid, durability, created_source, "
                        + "schema_version, created_at) SELECT ?, owner_uuid, item_uuid, "
                        + "definition_id, category, quality_seed, bound_owner_uuid, durability, "
                        + "created_source, schema_version, created_at FROM mmorpg_item_instance "
                        + "WHERE item_uuid = ? AND owner_uuid = ? AND location = 'INVENTORY' "
                        + "AND equipped_slot IS NULL AND bound_owner_uuid IS NULL "
                        + "AND category <> 'QUEST_TOKEN'")) {
            insert.setBytes(1, bytes(tradeId));
            insert.setBytes(2, bytes(itemId));
            insert.setBytes(3, bytes(owner));
            if (insert.executeUpdate() != 1) {
                reject("item is bound, quest, equipped, locked, invalid, or not owned " + itemId);
            }
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM mmorpg_item_instance WHERE item_uuid = ? AND owner_uuid = ?")) {
            delete.setBytes(1, bytes(itemId));
            delete.setBytes(2, bytes(owner));
            if (delete.executeUpdate() != 1) reject("item escrow race " + itemId);
        }
    }

    private static void restoreEscrowItem(
            Connection connection, UUID tradeId, UUID owner, UUID itemId) throws SQLException {
        insertEscrowItemForOwner(connection, tradeId, owner, owner, itemId);
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM mmorpg_trade_escrow_item "
                        + "WHERE trade_uuid = ? AND original_owner_uuid = ? AND item_uuid = ?")) {
            delete.setBytes(1, bytes(tradeId));
            delete.setBytes(2, bytes(owner));
            delete.setBytes(3, bytes(itemId));
            if (delete.executeUpdate() != 1) reject("unknown escrow item " + itemId);
        }
    }

    private static void insertEscrowItemForOwner(
            Connection connection, UUID tradeId, UUID originalOwner,
            UUID recipient, UUID itemId) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO mmorpg_item_instance "
                        + "(item_uuid, owner_uuid, definition_id, category, quality_seed, "
                        + "bound_owner_uuid, durability, created_source, schema_version, "
                        + "location, equipped_slot, created_at) SELECT item_uuid, ?, "
                        + "definition_id, category, quality_seed, bound_owner_uuid, durability, "
                        + "created_source, schema_version, 'PENDING', NULL, created_at "
                        + "FROM mmorpg_trade_escrow_item WHERE trade_uuid = ? "
                        + "AND original_owner_uuid = ? AND item_uuid = ?")) {
            insert.setBytes(1, bytes(recipient));
            insert.setBytes(2, bytes(tradeId));
            insert.setBytes(3, bytes(originalOwner));
            insert.setBytes(4, bytes(itemId));
            if (insert.executeUpdate() != 1) reject("failed to deliver escrow item " + itemId);
        }
    }

    private static void transferEscrow(Connection connection, TradeSnapshot trade)
            throws SQLException {
        for (var ownerOffer : trade.offers().entrySet()) {
            UUID recipient = trade.counterpart(ownerOffer.getKey());
            for (var material : ownerOffer.getValue().materials().entrySet()) {
                addPendingMaterial(connection, recipient, material.getKey(), material.getValue());
            }
            for (UUID itemId : ownerOffer.getValue().itemIds()) {
                insertEscrowItemForOwner(
                        connection, trade.tradeId(), ownerOffer.getKey(), recipient, itemId);
            }
        }
    }

    private static void returnEscrow(Connection connection, UUID tradeId) throws SQLException {
        TradeSnapshot trade = read(connection, tradeId, true).orElseThrow();
        for (var ownerOffer : trade.offers().entrySet()) {
            for (var material : ownerOffer.getValue().materials().entrySet()) {
                addPendingMaterial(connection, ownerOffer.getKey(),
                        material.getKey(), material.getValue());
            }
            for (UUID itemId : ownerOffer.getValue().itemIds()) {
                insertEscrowItemForOwner(connection, tradeId,
                        ownerOffer.getKey(), ownerOffer.getKey(), itemId);
            }
        }
    }

    private static void clearEscrow(Connection connection, UUID tradeId) throws SQLException {
        for (String table : java.util.List.of("mmorpg_trade_confirmation",
                "mmorpg_trade_escrow_material", "mmorpg_trade_escrow_item")) {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE trade_uuid = ?")) {
                delete.setBytes(1, bytes(tradeId));
                delete.executeUpdate();
            }
        }
    }

    private static void lockInventories(
            Connection connection, UUID first, UUID second) throws SQLException {
        UUID low = compare(first, second) <= 0 ? first : second;
        UUID high = low.equals(first) ? second : first;
        JdbcInventoryRepository.ensureInventory(connection, low);
        JdbcInventoryRepository.ensureInventory(connection, high);
        lockInventory(connection, low);
        lockInventory(connection, high);
    }

    private static void lockInventory(Connection connection, UUID player) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement(
                "SELECT player_uuid FROM mmorpg_inventory WHERE player_uuid = ? FOR UPDATE")) {
            lock.setBytes(1, bytes(player));
            try (ResultSet row = lock.executeQuery()) {
                if (!row.next()) throw new SQLException("inventory disappeared");
            }
        }
    }

    private static int compare(UUID left, UUID right) {
        return java.util.Arrays.compareUnsigned(bytes(left), bytes(right));
    }

    private static void updateState(Connection connection, TradeSnapshot trade,
                                    TradeState state, Instant now, boolean ignored)
            throws SQLException {
        bump(connection, trade, state);
    }

    private static void bump(
            Connection connection, TradeSnapshot trade, TradeState state) throws SQLException {
        updateRawState(connection, trade.tradeId(), state, trade.revision() + 1);
    }

    private static void updateRawState(
            Connection connection, UUID tradeId, TradeState state, long revision)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE mmorpg_trade SET trade_state = ?, trade_revision = ? "
                        + "WHERE trade_uuid = ?")) {
            update.setString(1, state.name());
            update.setLong(2, revision);
            update.setBytes(3, bytes(tradeId));
            if (update.executeUpdate() != 1) reject("trade state update failed");
        }
    }

    private static void releaseParticipantLocks(Connection connection, UUID tradeId)
            throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM mmorpg_trade_participant_lock WHERE trade_uuid = ?")) {
            delete.setBytes(1, bytes(tradeId));
            delete.executeUpdate();
        }
    }

    private static void expireCheck(TradeSnapshot trade, Instant now) {
        if (!trade.expiresAt().isAfter(now)) reject("trade expired");
    }

    private static void audit(
            Connection connection, UUID actor, String action, String subject) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO mmorpg_audit_log (actor_uuid, action, subject) VALUES (?, ?, ?)")) {
            statement.setBytes(1, bytes(actor));
            statement.setString(2, action);
            statement.setString(3, subject);
            statement.executeUpdate();
        }
    }

    private static void reject(String message) {
        throw new MMOException(ErrorCode.INVALID_ARGUMENT, message);
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
    private record Header(UUID requester, UUID recipient, TradeState state,
                          Instant createdAt, Instant expiresAt, long revision) {
    }
}
