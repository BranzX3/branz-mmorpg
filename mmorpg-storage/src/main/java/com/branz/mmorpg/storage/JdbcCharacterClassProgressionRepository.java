package com.branz.mmorpg.storage;

import com.branz.mmorpg.api.character.CharacterClassProgress;
import com.branz.mmorpg.api.character.CharacterClassProgressionRepository;
import com.branz.mmorpg.api.character.ClassProgressionMutationCommit;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
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

public final class JdbcCharacterClassProgressionRepository
        implements CharacterClassProgressionRepository {
    private final DatabaseManager database;

    public JdbcCharacterClassProgressionRepository(DatabaseManager database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public CharacterClassProgress load(UUID playerId, ContentId classId,
                                       int treeRevision, Instant now) {
        try {
            return database.inTransaction(connection ->
                    loadOne(connection, playerId, classId, treeRevision, now, false));
        } catch (SQLException exception) {
            throw failure("failed to load class progress for " + playerId, exception);
        }
    }

    @Override
    public ClassProgressionMutationCommit mutate(UUID playerId, ContentId classId,
                                                 int treeRevision, OperationId operationId,
                                                 String auditAction,
                                                 UnaryOperator<CharacterClassProgress> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (!playerId.equals(operationId.playerUuid())) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "operation player does not match class progress owner");
        }
        try {
            return database.inTransaction(connection -> {
                lockProfile(connection, playerId);
                CharacterClassProgress before = loadOne(connection, playerId, classId,
                        treeRevision, Instant.now(), true);
                if (!claim(connection, playerId, operationId)) {
                    return new ClassProgressionMutationCommit(false, before, before);
                }
                CharacterClassProgress after = Objects.requireNonNull(mutation.apply(before),
                        "class progression mutation returned null");
                if (!after.playerId().equals(playerId) || !after.classId().equals(classId)) {
                    throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                            "class progression mutation changed its owner");
                }
                save(connection, after);
                audit(connection, playerId, operationId, auditAction);
                return new ClassProgressionMutationCommit(true, before, after);
            });
        } catch (SQLException exception) {
            throw failure("failed class progression operation " + operationId, exception);
        }
    }

    private static CharacterClassProgress loadOne(Connection connection, UUID playerId,
                                                   ContentId classId, int treeRevision,
                                                   Instant now, boolean forUpdate) throws SQLException {
        String suffix = forUpdate ? " FOR UPDATE" : "";
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT class_id, level, total_xp, unspent_skill_points, tree_revision, updated_at "
                        + "FROM mmorpg_character_class_progress WHERE player_uuid = ?" + suffix)) {
            select.setBytes(1, bytes(playerId));
            try (ResultSet row = select.executeQuery()) {
                if (row.next()) {
                    ContentId storedClass = ContentId.parse(row.getString("class_id"));
                    if (!storedClass.equals(classId)) {
                        throw new MMOException(ErrorCode.STORAGE_FAILURE,
                                "stored class progress belongs to " + storedClass + ", expected " + classId);
                    }
                    return new CharacterClassProgress(playerId, storedClass, row.getInt("level"),
                            row.getLong("total_xp"), row.getInt("unspent_skill_points"),
                            row.getInt("tree_revision"), loadRanks(connection, playerId, storedClass),
                            row.getTimestamp("updated_at").toInstant());
                }
            }
        }
        return CharacterClassProgress.initial(playerId, classId, treeRevision, now);
    }

    private static Map<ContentId, Integer> loadRanks(Connection connection, UUID playerId,
                                                     ContentId classId) throws SQLException {
        Map<ContentId, Integer> result = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT node_id, node_rank FROM mmorpg_character_class_node_rank "
                        + "WHERE player_uuid = ? AND class_id = ?")) {
            select.setBytes(1, bytes(playerId));
            select.setString(2, classId.toString());
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) result.put(ContentId.parse(rows.getString("node_id")),
                        rows.getInt("node_rank"));
            }
        }
        return Map.copyOf(result);
    }

    private static void save(Connection connection, CharacterClassProgress progress)
            throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(
                "INSERT INTO mmorpg_character_class_progress "
                        + "(player_uuid, class_id, level, total_xp, unspent_skill_points, tree_revision, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                        + "class_id=VALUES(class_id), level=VALUES(level), total_xp=VALUES(total_xp), "
                        + "unspent_skill_points=VALUES(unspent_skill_points), "
                        + "tree_revision=VALUES(tree_revision), updated_at=VALUES(updated_at)")) {
            upsert.setBytes(1, bytes(progress.playerId()));
            upsert.setString(2, progress.classId().toString());
            upsert.setInt(3, progress.level());
            upsert.setLong(4, progress.totalXp());
            upsert.setInt(5, progress.unspentSkillPoints());
            upsert.setInt(6, progress.treeRevision());
            upsert.setTimestamp(7, Timestamp.from(progress.updatedAt()));
            upsert.executeUpdate();
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM mmorpg_character_class_node_rank WHERE player_uuid = ?")) {
            delete.setBytes(1, bytes(progress.playerId()));
            delete.executeUpdate();
        }
        if (progress.nodeRanks().isEmpty()) return;
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO mmorpg_character_class_node_rank "
                        + "(player_uuid, class_id, node_id, node_rank) VALUES (?, ?, ?, ?)")) {
            for (Map.Entry<ContentId, Integer> rank : progress.nodeRanks().entrySet()) {
                insert.setBytes(1, bytes(progress.playerId()));
                insert.setString(2, progress.classId().toString());
                insert.setString(3, rank.getKey().toString());
                insert.setInt(4, rank.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void lockProfile(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement(
                "SELECT player_uuid FROM mmorpg_player_profiles WHERE player_uuid = ? FOR UPDATE")) {
            lock.setBytes(1, bytes(playerId));
            try (ResultSet row = lock.executeQuery()) {
                if (!row.next()) throw new MMOException(ErrorCode.STORAGE_FAILURE, "player profile is missing");
            }
        }
    }

    private static boolean claim(Connection connection, UUID playerId, OperationId operation)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO mmorpg_processed_operation "
                        + "(operation_id, player_uuid, subsystem) VALUES (?, ?, ?)")) {
            statement.setString(1, operation.value());
            statement.setBytes(2, bytes(playerId));
            statement.setString(3, operation.subsystem());
            return statement.executeUpdate() == 1;
        }
    }

    private static void audit(Connection connection, UUID playerId, OperationId operation,
                              String action) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO mmorpg_audit_log (actor_uuid, action, subject) VALUES (?, ?, ?)")) {
            statement.setBytes(1, bytes(playerId));
            statement.setString(2, action == null ? "class_progression_mutation" : action);
            statement.setString(3, operation.value());
            statement.executeUpdate();
        }
    }

    private static byte[] bytes(UUID uuid) {
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits()).array();
    }

    private static MMOException failure(String message, Throwable cause) {
        return new MMOException(ErrorCode.STORAGE_FAILURE, message, cause);
    }
}
