package com.branz.mmorpg.storage;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.encounter.ContributionType;
import com.branz.mmorpg.api.encounter.EncounterRepository;
import com.branz.mmorpg.api.encounter.EncounterSnapshot;
import com.branz.mmorpg.api.encounter.EncounterState;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class JdbcEncounterRepository implements EncounterRepository {
    private final DatabaseManager database;

    public JdbcEncounterRepository(DatabaseManager database) {
        this.database = java.util.Objects.requireNonNull(database, "database");
    }

    @Override public EncounterSnapshot insert(EncounterSnapshot encounter) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO mmorpg_encounter (encounter_uuid, definition_id, "
                                + "encounter_state, phase_index, attempt, completion_id, "
                                + "created_at, state_since, encounter_revision) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    bindHeader(statement, encounter, false);
                    statement.executeUpdate();
                }
                writeChildren(connection, encounter);
                return encounter;
            });
        } catch (SQLException failure) {
            throw storage("failed to insert encounter " + encounter.instanceId(), failure);
        }
    }

    @Override public Optional<EncounterSnapshot> find(UUID instanceId) {
        try {
            return database.inTransaction(connection -> read(connection, instanceId, false));
        } catch (SQLException failure) {
            throw storage("failed to read encounter " + instanceId, failure);
        }
    }

    @Override public Collection<EncounterSnapshot> recoverable() {
        try {
            return database.inTransaction(connection -> {
                ArrayList<UUID> ids = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT encounter_uuid FROM mmorpg_encounter "
                                + "WHERE encounter_state <> 'CLOSED' ORDER BY created_at");
                     ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) ids.add(uuid(rows.getBytes(1)));
                }
                ArrayList<EncounterSnapshot> result = new ArrayList<>();
                for (UUID id : ids) result.add(read(connection, id, false).orElseThrow());
                return java.util.List.copyOf(result);
            });
        } catch (SQLException failure) {
            throw storage("failed to recover encounters", failure);
        }
    }

    @Override public EncounterSnapshot save(EncounterSnapshot encounter) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE mmorpg_encounter SET definition_id = ?, encounter_state = ?, "
                                + "phase_index = ?, attempt = ?, completion_id = ?, created_at = ?, "
                                + "state_since = ?, encounter_revision = ? "
                                + "WHERE encounter_uuid = ? AND encounter_revision = ?")) {
                    bindHeader(statement, encounter, true);
                    statement.setLong(10, encounter.revision() - 1);
                    if (statement.executeUpdate() != 1) {
                        Optional<EncounterSnapshot> persisted =
                                read(connection, encounter.instanceId(), true);
                        if (persisted.isPresent()
                                && persisted.orElseThrow().revision() >= encounter.revision()) {
                            return persisted.orElseThrow();
                        }
                        throw new MMOException(ErrorCode.STORAGE_FAILURE,
                                "encounter optimistic revision conflict "
                                        + encounter.instanceId());
                    }
                }
                deleteChildren(connection, encounter.instanceId());
                writeChildren(connection, encounter);
                return encounter;
            });
        } catch (SQLException failure) {
            throw storage("failed to save encounter " + encounter.instanceId(), failure);
        }
    }

    private static Optional<EncounterSnapshot> read(
            Connection connection, UUID id, boolean lock) throws SQLException {
        Header header;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT definition_id, encounter_state, phase_index, attempt, completion_id, "
                        + "created_at, state_since, encounter_revision FROM mmorpg_encounter "
                        + "WHERE encounter_uuid = ?" + (lock ? " FOR UPDATE" : ""))) {
            statement.setBytes(1, bytes(id));
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                header = new Header(ContentId.parse(row.getString("definition_id")),
                        EncounterState.valueOf(row.getString("encounter_state")),
                        row.getInt("phase_index"), row.getInt("attempt"),
                        Optional.ofNullable(row.getString("completion_id")),
                        row.getTimestamp("created_at").toInstant(),
                        row.getTimestamp("state_since").toInstant(),
                        row.getLong("encounter_revision"));
            }
        }
        HashSet<UUID> participants = new HashSet<>();
        HashSet<UUID> connected = new HashSet<>();
        HashSet<UUID> rewarded = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT player_uuid, connected, rewarded FROM mmorpg_encounter_participant "
                        + "WHERE encounter_uuid = ?")) {
            statement.setBytes(1, bytes(id));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID player = uuid(rows.getBytes("player_uuid"));
                    participants.add(player);
                    if (rows.getBoolean("connected")) connected.add(player);
                    if (rows.getBoolean("rewarded")) rewarded.add(player);
                }
            }
        }
        HashMap<UUID, java.util.Map<ContributionType, Double>> contributions = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT player_uuid, contribution_type, amount "
                        + "FROM mmorpg_encounter_contribution WHERE encounter_uuid = ?")) {
            statement.setBytes(1, bytes(id));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID player = uuid(rows.getBytes("player_uuid"));
                    contributions.computeIfAbsent(
                                    player, ignored -> new EnumMap<>(ContributionType.class))
                            .put(ContributionType.valueOf(rows.getString("contribution_type")),
                                    rows.getDouble("amount"));
                }
            }
        }
        Set<UUID> actors = readUuids(connection, "mmorpg_encounter_actor",
                "actor_uuid", id);
        Set<String> chunks = readStrings(connection, "mmorpg_encounter_forced_chunk",
                "chunk_key", id);
        return Optional.of(new EncounterSnapshot(id, header.definitionId(), header.state(),
                header.phaseIndex(), header.attempt(), participants, connected, contributions,
                actors, chunks, header.completionId(), rewarded, header.createdAt(),
                header.stateSince(), header.revision()));
    }

    private static void writeChildren(Connection connection, EncounterSnapshot encounter)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO mmorpg_encounter_participant "
                        + "(encounter_uuid, player_uuid, connected, rewarded) VALUES (?, ?, ?, ?)")) {
            for (UUID player : encounter.participantSnapshot()) {
                statement.setBytes(1, bytes(encounter.instanceId()));
                statement.setBytes(2, bytes(player));
                statement.setBoolean(3, encounter.connectedParticipants().contains(player));
                statement.setBoolean(4, encounter.rewardedPlayers().contains(player));
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO mmorpg_encounter_contribution "
                        + "(encounter_uuid, player_uuid, contribution_type, amount) "
                        + "VALUES (?, ?, ?, ?)")) {
            for (var player : encounter.contributions().entrySet()) {
                for (var contribution : player.getValue().entrySet()) {
                    statement.setBytes(1, bytes(encounter.instanceId()));
                    statement.setBytes(2, bytes(player.getKey()));
                    statement.setString(3, contribution.getKey().name());
                    statement.setDouble(4, contribution.getValue());
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
        writeUuids(connection, encounter.instanceId(), "mmorpg_encounter_actor",
                "actor_uuid", encounter.actorIds());
        writeStrings(connection, encounter.instanceId(), "mmorpg_encounter_forced_chunk",
                "chunk_key", encounter.forcedChunkKeys());
    }

    private static void deleteChildren(Connection connection, UUID id) throws SQLException {
        for (String table : java.util.List.of("mmorpg_encounter_contribution",
                "mmorpg_encounter_participant", "mmorpg_encounter_actor",
                "mmorpg_encounter_forced_chunk")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE encounter_uuid = ?")) {
                statement.setBytes(1, bytes(id));
                statement.executeUpdate();
            }
        }
    }

    private static Set<UUID> readUuids(
            Connection connection, String table, String column, UUID id) throws SQLException {
        HashSet<UUID> result = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + " FROM " + table + " WHERE encounter_uuid = ?")) {
            statement.setBytes(1, bytes(id));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(uuid(rows.getBytes(1)));
            }
        }
        return result;
    }

    private static Set<String> readStrings(
            Connection connection, String table, String column, UUID id) throws SQLException {
        HashSet<String> result = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + " FROM " + table + " WHERE encounter_uuid = ?")) {
            statement.setBytes(1, bytes(id));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(rows.getString(1));
            }
        }
        return result;
    }

    private static void writeUuids(Connection connection, UUID id, String table,
                                   String column, Collection<UUID> values) throws SQLException {
        if (values.isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + table + " (encounter_uuid, " + column + ") VALUES (?, ?)")) {
            for (UUID value : values) {
                statement.setBytes(1, bytes(id));
                statement.setBytes(2, bytes(value));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void writeStrings(Connection connection, UUID id, String table,
                                     String column, Collection<String> values) throws SQLException {
        if (values.isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + table + " (encounter_uuid, " + column + ") VALUES (?, ?)")) {
            for (String value : values) {
                statement.setBytes(1, bytes(id));
                statement.setString(2, value);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void bindHeader(
            PreparedStatement statement, EncounterSnapshot encounter, boolean update)
            throws SQLException {
        int index = 1;
        if (!update) statement.setBytes(index++, bytes(encounter.instanceId()));
        statement.setString(index++, encounter.definitionId().toString());
        statement.setString(index++, encounter.state().name());
        statement.setInt(index++, encounter.phaseIndex());
        statement.setInt(index++, encounter.attempt());
        if (encounter.completionId().isPresent()) {
            statement.setString(index++, encounter.completionId().orElseThrow());
        } else {
            statement.setNull(index++, java.sql.Types.VARCHAR);
        }
        statement.setTimestamp(index++, Timestamp.from(encounter.createdAt()));
        statement.setTimestamp(index++, Timestamp.from(encounter.stateSince()));
        statement.setLong(index++, encounter.revision());
        if (update) statement.setBytes(index, bytes(encounter.instanceId()));
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

    private record Header(ContentId definitionId, EncounterState state, int phaseIndex,
                          int attempt, Optional<String> completionId,
                          java.time.Instant createdAt, java.time.Instant stateSince,
                          long revision) {
    }
}
