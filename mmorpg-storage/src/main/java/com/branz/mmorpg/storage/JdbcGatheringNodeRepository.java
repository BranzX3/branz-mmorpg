package com.branz.mmorpg.storage;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.gathering.GatheringHarvestCommit;
import com.branz.mmorpg.api.gathering.GatheringNodeInstance;
import com.branz.mmorpg.api.gathering.GatheringNodeRepository;
import com.branz.mmorpg.api.gathering.GatheringNodeState;
import com.branz.mmorpg.api.gathering.WorldBlockPosition;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.lifeskill.LifeSkillProfile;
import com.branz.mmorpg.api.lifeskill.LifeSkillSnapshot;
import com.branz.mmorpg.api.operation.OperationId;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public final class JdbcGatheringNodeRepository implements GatheringNodeRepository {
    private final DatabaseManager database;

    public JdbcGatheringNodeRepository(DatabaseManager database) {
        this.database = java.util.Objects.requireNonNull(database, "database");
    }

    @Override
    public GatheringNodeInstance place(GatheringNodeInstance node) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO mmorpg_gathering_node "
                                + "(node_uuid, definition_id, world_uuid, block_x, block_y, block_z, "
                                + "node_state, reservation_sequence, respawn_at, reserved_by, "
                                + "reserved_until, last_harvested_by, last_harvested_at, "
                                + "created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    bind(insert, node);
                    insert.executeUpdate();
                }
                audit(connection, node.createdBy(), "gathering_node_place",
                        node.instanceId().toString());
                return node;
            });
        } catch (SQLException failure) {
            throw storage("failed to place gathering node", failure);
        }
    }

    @Override public Optional<GatheringNodeInstance> findAt(WorldBlockPosition position) {
        return queryOne("world_uuid = ? AND block_x = ? AND block_y = ? AND block_z = ?",
                statement -> {
                    statement.setBytes(1, bytes(position.worldId()));
                    statement.setInt(2, position.x());
                    statement.setInt(3, position.y());
                    statement.setInt(4, position.z());
                });
    }

    @Override public Optional<GatheringNodeInstance> find(UUID instanceId) {
        return queryOne("node_uuid = ?", statement ->
                statement.setBytes(1, bytes(instanceId)));
    }

    @Override public Collection<GatheringNodeInstance> list() {
        try {
            return database.inTransaction(connection -> {
                ArrayList<GatheringNodeInstance> result = new ArrayList<>();
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT * FROM mmorpg_gathering_node ORDER BY created_at, node_uuid");
                     ResultSet rows = select.executeQuery()) {
                    while (rows.next()) result.add(read(rows));
                }
                return java.util.List.copyOf(result);
            });
        } catch (SQLException failure) {
            throw storage("failed to list gathering nodes", failure);
        }
    }

    @Override public boolean remove(UUID instanceId) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM mmorpg_gathering_node WHERE node_uuid = ?")) {
                    delete.setBytes(1, bytes(instanceId));
                    return delete.executeUpdate() == 1;
                }
            });
        } catch (SQLException failure) {
            throw storage("failed to remove gathering node " + instanceId, failure);
        }
    }

    @Override
    public GatheringNodeInstance reserve(
            UUID instanceId, UUID playerId, Instant now,
            Duration harvestTime, Duration grace) {
        try {
            return database.inTransaction(connection -> {
                releaseExpired(connection, now);
                GatheringNodeInstance node = require(connection, instanceId, true);
                node = normalizeDepleted(node, now);
                if (node.state() == GatheringNodeState.BROKEN) reject("NODE_BROKEN");
                if (node.state() == GatheringNodeState.DEPLETED) reject("NODE_DEPLETED");
                if (node.state() == GatheringNodeState.RESERVED) reject("NODE_TAKEN");
                try (PreparedStatement held = connection.prepareStatement(
                        "SELECT node_uuid FROM mmorpg_gathering_node "
                                + "WHERE reserved_by = ? AND node_state = 'RESERVED' "
                                + "AND reserved_until > ? LIMIT 1 FOR UPDATE")) {
                    held.setBytes(1, bytes(playerId));
                    held.setTimestamp(2, Timestamp.from(now));
                    try (ResultSet row = held.executeQuery()) {
                        if (row.next()) reject("ALREADY_RESERVED");
                    }
                }
                GatheringNodeInstance reserved = new GatheringNodeInstance(
                        node.instanceId(), node.definitionId(), node.position(),
                        GatheringNodeState.RESERVED,
                        Math.addExact(node.reservationSequence(), 1),
                        Optional.empty(), Optional.of(playerId),
                        Optional.of(now.plus(harvestTime).plus(grace)),
                        node.lastHarvestedBy(), node.lastHarvestedAt(),
                        node.createdBy(), node.createdAt());
                update(connection, reserved);
                return reserved;
            });
        } catch (SQLException failure) {
            throw storage("failed to reserve gathering node " + instanceId, failure);
        }
    }

    @Override
    public GatheringNodeInstance release(
            UUID instanceId, UUID playerId, long sequence, Instant now) {
        try {
            return database.inTransaction(connection -> {
                GatheringNodeInstance node = require(connection, instanceId, true);
                if (node.state() != GatheringNodeState.RESERVED) return node;
                if (!node.reservedBy().orElseThrow().equals(playerId)
                        || node.reservationSequence() != sequence) reject("INTERRUPTED");
                GatheringNodeInstance available = available(node);
                update(connection, available);
                return available;
            });
        } catch (SQLException failure) {
            throw storage("failed to release gathering node " + instanceId, failure);
        }
    }

    @Override
    public GatheringHarvestCommit commitHarvest(
            UUID instanceId, UUID playerId, long reservationSequence,
            ContentId skillId, OperationId operationId, Instant now, Instant respawnAt,
            UnaryOperator<LifeSkillSnapshot> skillMutation,
            UnaryOperator<InventorySnapshot> inventoryMutation) {
        if (!playerId.equals(operationId.playerUuid())) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "operation player does not match harvester");
        }
        if (!respawnAt.isAfter(now)) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "gathering respawn time must be in the future");
        }
        try {
            return database.inTransaction(connection -> {
                JdbcPlayerProfileRepository.lockPlayer(connection, playerId);
                GatheringNodeInstance node = require(connection, instanceId, true);
                LifeSkillProfile profile =
                        JdbcPlayerProfileRepository.readLifeSkills(connection, playerId);
                LifeSkillSnapshot skillBefore = profile.skill(skillId);
                JdbcInventoryRepository.ensureInventory(connection, playerId);
                InventorySnapshot inventoryBefore =
                        JdbcInventoryRepository.loadSnapshot(connection, playerId, true);
                if (!claim(connection, playerId, operationId)) {
                    return new GatheringHarvestCommit(false, node, node,
                            skillBefore, skillBefore, inventoryBefore, inventoryBefore);
                }
                requireReservation(node, playerId, reservationSequence, now);
                LifeSkillSnapshot skillAfter =
                        java.util.Objects.requireNonNull(skillMutation.apply(skillBefore));
                InventorySnapshot inventoryAfter =
                        java.util.Objects.requireNonNull(inventoryMutation.apply(inventoryBefore));
                if (!skillId.equals(skillAfter.skillId())
                        || !playerId.equals(inventoryAfter.playerId())) {
                    throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                            "harvest mutation changed authoritative identity");
                }
                GatheringNodeInstance depleted = new GatheringNodeInstance(
                        node.instanceId(), node.definitionId(), node.position(),
                        GatheringNodeState.DEPLETED, node.reservationSequence(),
                        Optional.of(respawnAt), Optional.empty(), Optional.empty(),
                        Optional.of(playerId), Optional.of(now),
                        node.createdBy(), node.createdAt());
                update(connection, depleted);
                JdbcPlayerProfileRepository.writeLifeSkills(
                        connection, profile.with(skillAfter));
                JdbcInventoryRepository.replaceSnapshot(connection, inventoryAfter);
                audit(connection, playerId, "gathering_harvest", operationId.value());
                return new GatheringHarvestCommit(true, node, depleted,
                        skillBefore, skillAfter, inventoryBefore, inventoryAfter);
            });
        } catch (SQLException failure) {
            throw storage("failed gathering harvest " + operationId, failure);
        }
    }

    @Override
    public GatheringNodeInstance setState(
            UUID instanceId, GatheringNodeState state, Instant now) {
        if (state != GatheringNodeState.AVAILABLE && state != GatheringNodeState.BROKEN) {
            throw new IllegalArgumentException("operator state must be AVAILABLE or BROKEN");
        }
        try {
            return database.inTransaction(connection -> {
                GatheringNodeInstance node = require(connection, instanceId, true);
                GatheringNodeInstance changed = state == GatheringNodeState.AVAILABLE
                        ? available(node)
                        : new GatheringNodeInstance(node.instanceId(), node.definitionId(),
                                node.position(), GatheringNodeState.BROKEN,
                                node.reservationSequence(), Optional.empty(), Optional.empty(),
                                Optional.empty(), node.lastHarvestedBy(), node.lastHarvestedAt(),
                                node.createdBy(), node.createdAt());
                update(connection, changed);
                return changed;
            });
        } catch (SQLException failure) {
            throw storage("failed to change gathering node " + instanceId, failure);
        }
    }

    private Optional<GatheringNodeInstance> queryOne(String where, Binder binder) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT * FROM mmorpg_gathering_node WHERE " + where)) {
                    binder.bind(select);
                    try (ResultSet row = select.executeQuery()) {
                        return row.next() ? Optional.of(read(row)) : Optional.empty();
                    }
                }
            });
        } catch (SQLException failure) {
            throw storage("failed gathering node query", failure);
        }
    }

    private static GatheringNodeInstance require(
            Connection connection, UUID id, boolean lock) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT * FROM mmorpg_gathering_node WHERE node_uuid = ?"
                        + (lock ? " FOR UPDATE" : ""))) {
            select.setBytes(1, bytes(id));
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) throw new MMOException(
                        ErrorCode.INVALID_ARGUMENT, "unknown gathering node " + id);
                return read(row);
            }
        }
    }

    private static GatheringNodeInstance read(ResultSet row) throws SQLException {
        return new GatheringNodeInstance(
                uuid(row.getBytes("node_uuid")),
                ContentId.parse(row.getString("definition_id")),
                new WorldBlockPosition(uuid(row.getBytes("world_uuid")),
                        row.getInt("block_x"), row.getInt("block_y"), row.getInt("block_z")),
                GatheringNodeState.valueOf(row.getString("node_state")),
                row.getLong("reservation_sequence"),
                instant(row, "respawn_at"), optionalUuid(row, "reserved_by"),
                instant(row, "reserved_until"), optionalUuid(row, "last_harvested_by"),
                instant(row, "last_harvested_at"), uuid(row.getBytes("created_by")),
                row.getTimestamp("created_at").toInstant());
    }

    private static void bind(PreparedStatement statement, GatheringNodeInstance node)
            throws SQLException {
        statement.setBytes(1, bytes(node.instanceId()));
        statement.setString(2, node.definitionId().toString());
        statement.setBytes(3, bytes(node.position().worldId()));
        statement.setInt(4, node.position().x());
        statement.setInt(5, node.position().y());
        statement.setInt(6, node.position().z());
        statement.setString(7, node.state().name());
        statement.setLong(8, node.reservationSequence());
        timestamp(statement, 9, node.respawnAt());
        uuid(statement, 10, node.reservedBy());
        timestamp(statement, 11, node.reservedUntil());
        uuid(statement, 12, node.lastHarvestedBy());
        timestamp(statement, 13, node.lastHarvestedAt());
        statement.setBytes(14, bytes(node.createdBy()));
        statement.setTimestamp(15, Timestamp.from(node.createdAt()));
    }

    private static void update(Connection connection, GatheringNodeInstance node)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE mmorpg_gathering_node SET definition_id=?, world_uuid=?, block_x=?, "
                        + "block_y=?, block_z=?, node_state=?, reservation_sequence=?, "
                        + "respawn_at=?, reserved_by=?, reserved_until=?, last_harvested_by=?, "
                        + "last_harvested_at=? WHERE node_uuid=?")) {
            update.setString(1, node.definitionId().toString());
            update.setBytes(2, bytes(node.position().worldId()));
            update.setInt(3, node.position().x());
            update.setInt(4, node.position().y());
            update.setInt(5, node.position().z());
            update.setString(6, node.state().name());
            update.setLong(7, node.reservationSequence());
            timestamp(update, 8, node.respawnAt());
            uuid(update, 9, node.reservedBy());
            timestamp(update, 10, node.reservedUntil());
            uuid(update, 11, node.lastHarvestedBy());
            timestamp(update, 12, node.lastHarvestedAt());
            update.setBytes(13, bytes(node.instanceId()));
            update.executeUpdate();
        }
    }

    private static void releaseExpired(Connection connection, Instant now) throws SQLException {
        try (PreparedStatement release = connection.prepareStatement(
                "UPDATE mmorpg_gathering_node SET node_state='AVAILABLE', reserved_by=NULL, "
                        + "reserved_until=NULL, respawn_at=NULL "
                        + "WHERE node_state='RESERVED' AND reserved_until <= ?")) {
            release.setTimestamp(1, Timestamp.from(now));
            release.executeUpdate();
        }
    }

    private static GatheringNodeInstance normalizeDepleted(
            GatheringNodeInstance node, Instant now) {
        return node.state() == GatheringNodeState.DEPLETED
                && !node.respawnAt().orElseThrow().isAfter(now) ? available(node) : node;
    }

    private static GatheringNodeInstance available(GatheringNodeInstance node) {
        return new GatheringNodeInstance(node.instanceId(), node.definitionId(), node.position(),
                GatheringNodeState.AVAILABLE, node.reservationSequence(), Optional.empty(),
                Optional.empty(), Optional.empty(), node.lastHarvestedBy(),
                node.lastHarvestedAt(), node.createdBy(), node.createdAt());
    }

    private static void requireReservation(
            GatheringNodeInstance node, UUID playerId, long sequence, Instant now) {
        if (node.state() != GatheringNodeState.RESERVED
                || !node.reservedBy().orElseThrow().equals(playerId)
                || node.reservationSequence() != sequence
                || !node.reservedUntil().orElseThrow().isAfter(now)) reject("INTERRUPTED");
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

    private static void audit(Connection connection, UUID actor, String action, String subject)
            throws SQLException {
        try (PreparedStatement audit = connection.prepareStatement(
                "INSERT INTO mmorpg_audit_log (actor_uuid, action, subject) VALUES (?, ?, ?)")) {
            audit.setBytes(1, bytes(actor));
            audit.setString(2, action);
            audit.setString(3, subject);
            audit.executeUpdate();
        }
    }

    private static Optional<Instant> instant(ResultSet row, String name) throws SQLException {
        Timestamp value = row.getTimestamp(name);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    private static Optional<UUID> optionalUuid(ResultSet row, String name) throws SQLException {
        byte[] value = row.getBytes(name);
        return value == null ? Optional.empty() : Optional.of(uuid(value));
    }

    private static void timestamp(
            PreparedStatement statement, int index, Optional<Instant> value) throws SQLException {
        if (value.isPresent()) statement.setTimestamp(index, Timestamp.from(value.get()));
        else statement.setNull(index, java.sql.Types.TIMESTAMP);
    }

    private static void uuid(
            PreparedStatement statement, int index, Optional<UUID> value) throws SQLException {
        if (value.isPresent()) statement.setBytes(index, bytes(value.get()));
        else statement.setNull(index, java.sql.Types.BINARY);
    }

    private static byte[] bytes(UUID value) {
        return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits()).array();
    }

    private static UUID uuid(byte[] value) {
        ByteBuffer buffer = ByteBuffer.wrap(value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static void reject(String reason) {
        throw new MMOException(ErrorCode.INVALID_ARGUMENT, reason);
    }

    private static MMOException storage(String message, Throwable cause) {
        return new MMOException(ErrorCode.STORAGE_FAILURE, message, cause);
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
