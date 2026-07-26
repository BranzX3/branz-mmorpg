package com.branz.mmorpg.storage;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.mob.MobAiState;
import com.branz.mmorpg.api.mob.MobRepository;
import com.branz.mmorpg.api.mob.MobRuntimeSnapshot;
import com.branz.mmorpg.api.mob.SpatialPosition;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public final class JdbcMobRepository implements MobRepository {
    private static final String COLUMNS = "mob_uuid, definition_id, mob_level, world_uuid, "
            + "home_x, home_y, home_z, position_x, position_y, position_z, ai_state, "
            + "target_uuid, health, maximum_health, state_since, next_decision_at, "
            + "next_path_request_at, decision_sequence, reward_sequence";
    private final DatabaseManager database;

    public JdbcMobRepository(DatabaseManager database) {
        this.database = java.util.Objects.requireNonNull(database, "database");
    }

    @Override public MobRuntimeSnapshot insert(MobRuntimeSnapshot mob) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO mmorpg_mob_runtime (" + COLUMNS
                                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    bind(statement, mob);
                    statement.executeUpdate();
                }
                return mob;
            });
        } catch (SQLException failure) {
            throw storage("failed to insert mob " + mob.instanceId(), failure);
        }
    }

    @Override public Optional<MobRuntimeSnapshot> find(UUID instanceId) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT " + COLUMNS + " FROM mmorpg_mob_runtime WHERE mob_uuid = ?")) {
                    statement.setBytes(1, bytes(instanceId));
                    try (ResultSet row = statement.executeQuery()) {
                        return row.next() ? Optional.of(read(row)) : Optional.empty();
                    }
                }
            });
        } catch (SQLException failure) {
            throw storage("failed to read mob " + instanceId, failure);
        }
    }

    @Override public Collection<MobRuntimeSnapshot> list() {
        try {
            return database.inTransaction(connection -> {
                ArrayList<MobRuntimeSnapshot> result = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT " + COLUMNS + " FROM mmorpg_mob_runtime ORDER BY mob_uuid");
                     ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(read(rows));
                }
                return java.util.List.copyOf(result);
            });
        } catch (SQLException failure) {
            throw storage("failed to list mobs", failure);
        }
    }

    @Override public MobRuntimeSnapshot save(MobRuntimeSnapshot mob) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE mmorpg_mob_runtime SET definition_id = ?, mob_level = ?, "
                                + "world_uuid = ?, home_x = ?, home_y = ?, home_z = ?, "
                                + "position_x = ?, position_y = ?, position_z = ?, ai_state = ?, "
                                + "target_uuid = ?, health = ?, maximum_health = ?, state_since = ?, "
                                + "next_decision_at = ?, next_path_request_at = ?, "
                                + "decision_sequence = ?, reward_sequence = ? WHERE mob_uuid = ?")) {
                    bindUpdate(statement, mob);
                    if (statement.executeUpdate() != 1) {
                        throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                                "unknown mob " + mob.instanceId());
                    }
                }
                return mob;
            });
        } catch (SQLException failure) {
            throw storage("failed to save mob " + mob.instanceId(), failure);
        }
    }

    @Override public boolean remove(UUID instanceId) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM mmorpg_mob_runtime WHERE mob_uuid = ?")) {
                    statement.setBytes(1, bytes(instanceId));
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException failure) {
            throw storage("failed to remove mob " + instanceId, failure);
        }
    }

    private static MobRuntimeSnapshot read(ResultSet row) throws SQLException {
        UUID world = uuid(row.getBytes("world_uuid"));
        byte[] target = row.getBytes("target_uuid");
        return new MobRuntimeSnapshot(
                uuid(row.getBytes("mob_uuid")), ContentId.parse(row.getString("definition_id")),
                row.getInt("mob_level"),
                new SpatialPosition(world, row.getDouble("home_x"), row.getDouble("home_y"),
                        row.getDouble("home_z")),
                new SpatialPosition(world, row.getDouble("position_x"),
                        row.getDouble("position_y"), row.getDouble("position_z")),
                MobAiState.valueOf(row.getString("ai_state")),
                target == null ? Optional.empty() : Optional.of(uuid(target)),
                row.getDouble("health"), row.getDouble("maximum_health"),
                row.getTimestamp("state_since").toInstant(),
                row.getTimestamp("next_decision_at").toInstant(),
                row.getTimestamp("next_path_request_at").toInstant(),
                row.getLong("decision_sequence"), row.getLong("reward_sequence"));
    }

    private static void bind(PreparedStatement statement, MobRuntimeSnapshot mob)
            throws SQLException {
        statement.setBytes(1, bytes(mob.instanceId()));
        statement.setString(2, mob.definitionId().toString());
        statement.setInt(3, mob.level());
        bindBody(statement, 4, mob);
    }

    private static void bindUpdate(PreparedStatement statement, MobRuntimeSnapshot mob)
            throws SQLException {
        statement.setString(1, mob.definitionId().toString());
        statement.setInt(2, mob.level());
        bindBody(statement, 3, mob);
        statement.setBytes(19, bytes(mob.instanceId()));
    }

    private static void bindBody(PreparedStatement statement, int start, MobRuntimeSnapshot mob)
            throws SQLException {
        int index = start;
        statement.setBytes(index++, bytes(mob.home().worldId()));
        statement.setDouble(index++, mob.home().x());
        statement.setDouble(index++, mob.home().y());
        statement.setDouble(index++, mob.home().z());
        statement.setDouble(index++, mob.position().x());
        statement.setDouble(index++, mob.position().y());
        statement.setDouble(index++, mob.position().z());
        statement.setString(index++, mob.state().name());
        if (mob.targetId().isPresent()) statement.setBytes(index++, bytes(mob.targetId().orElseThrow()));
        else statement.setNull(index++, java.sql.Types.BINARY);
        statement.setDouble(index++, mob.health());
        statement.setDouble(index++, mob.maximumHealth());
        statement.setTimestamp(index++, Timestamp.from(mob.stateSince()));
        statement.setTimestamp(index++, Timestamp.from(mob.nextDecisionAt()));
        statement.setTimestamp(index++, Timestamp.from(mob.nextPathRequestAt()));
        statement.setLong(index++, mob.decisionSequence());
        statement.setLong(index, mob.rewardSequence());
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
