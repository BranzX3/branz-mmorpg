package com.branz.mmorpg.storage;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.mastery.CombatMasteryRepository;
import com.branz.mmorpg.api.mastery.MasteryMutationCommit;
import com.branz.mmorpg.api.mastery.MasterySnapshot;
import com.branz.mmorpg.api.operation.OperationId;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;

public final class JdbcCombatMasteryRepository implements CombatMasteryRepository {

    private final DatabaseManager database;

    public JdbcCombatMasteryRepository(DatabaseManager database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public Map<ContentId, MasterySnapshot> load(UUID playerId) {
        try {
            return database.inTransaction(connection -> loadAll(connection, playerId));
        } catch (SQLException exception) {
            throw failure("failed to load combat mastery for " + playerId, exception);
        }
    }

    @Override
    public MasteryMutationCommit mutate(UUID playerId, ContentId masteryId,
                                        OperationId operationId, long awardedXp,
                                        UnaryOperator<MasterySnapshot> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (!playerId.equals(operationId.playerUuid())) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "operation player does not match mastery owner");
        }
        try {
            return database.inTransaction(connection -> {
                MasterySnapshot before = loadOne(connection, playerId, masteryId);
                if (!claim(connection, playerId, operationId)) {
                    return new MasteryMutationCommit(false, before, before, 0L);
                }
                MasterySnapshot after = Objects.requireNonNull(mutation.apply(before),
                        "mastery mutation returned null");
                if (!masteryId.equals(after.masteryId())) {
                    throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                            "mastery mutation changed its content ID");
                }
                try (PreparedStatement upsert = connection.prepareStatement(
                        "INSERT INTO mmorpg_combat_mastery "
                                + "(player_uuid, mastery_id, level, total_xp, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                                + "level = VALUES(level), total_xp = VALUES(total_xp), "
                                + "updated_at = VALUES(updated_at)")) {
                    upsert.setBytes(1, bytes(playerId));
                    upsert.setString(2, masteryId.toString());
                    upsert.setInt(3, after.level());
                    upsert.setLong(4, after.totalXp());
                    upsert.setTimestamp(5, Timestamp.from(after.updatedAt()));
                    upsert.executeUpdate();
                }
                audit(connection, playerId, operationId);
                return new MasteryMutationCommit(true, before, after, awardedXp);
            });
        } catch (SQLException exception) {
            throw failure("failed mastery operation " + operationId, exception);
        }
    }

    private static Map<ContentId, MasterySnapshot> loadAll(Connection connection, UUID playerId)
            throws SQLException {
        Map<ContentId, MasterySnapshot> result = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT mastery_id, level, total_xp, updated_at "
                        + "FROM mmorpg_combat_mastery WHERE player_uuid = ?")) {
            select.setBytes(1, bytes(playerId));
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    ContentId id = ContentId.parse(rows.getString("mastery_id"));
                    result.put(id, new MasterySnapshot(id, rows.getInt("level"),
                            rows.getLong("total_xp"), rows.getTimestamp("updated_at").toInstant()));
                }
            }
        }
        return Map.copyOf(result);
    }

    private static MasterySnapshot loadOne(Connection connection, UUID playerId, ContentId masteryId)
            throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT level, total_xp, updated_at FROM mmorpg_combat_mastery "
                        + "WHERE player_uuid = ? AND mastery_id = ? FOR UPDATE")) {
            select.setBytes(1, bytes(playerId));
            select.setString(2, masteryId.toString());
            try (ResultSet row = select.executeQuery()) {
                if (row.next()) {
                    return new MasterySnapshot(masteryId, row.getInt("level"),
                            row.getLong("total_xp"), row.getTimestamp("updated_at").toInstant());
                }
            }
        }
        return MasterySnapshot.untrained(masteryId, Instant.now());
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
            audit.setString(2, "combat_mastery_mutation");
            audit.setString(3, operation.value());
            audit.executeUpdate();
        }
    }

    private static byte[] bytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
    }

    private static MMOException failure(String message, Throwable cause) {
        return new MMOException(ErrorCode.STORAGE_FAILURE, message, cause);
    }
}
