package com.branz.mmorpg.storage;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.social.PartyRepository;
import com.branz.mmorpg.api.social.PartySnapshot;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

public final class JdbcPartyRepository implements PartyRepository {
    private final DatabaseManager database;

    public JdbcPartyRepository(DatabaseManager database) {
        this.database = java.util.Objects.requireNonNull(database, "database");
    }

    @Override public PartySnapshot insert(PartySnapshot party) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO mmorpg_party (party_uuid, leader_uuid, maximum_members, "
                                + "reward_range, rewards_same_world, created_at, party_revision) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    statement.setBytes(1, bytes(party.partyId()));
                    bind(statement, party, 2);
                    statement.executeUpdate();
                }
                writeChildren(connection, party);
                return party;
            });
        } catch (SQLException failure) {
            throw storage("failed to create party", failure);
        }
    }

    @Override public Optional<PartySnapshot> find(UUID partyId) {
        try {
            return database.inTransaction(connection -> read(connection, partyId, false));
        } catch (SQLException failure) {
            throw storage("failed to read party " + partyId, failure);
        }
    }

    @Override public Optional<PartySnapshot> findByMember(UUID playerId) {
        try {
            return database.inTransaction(connection -> {
                UUID partyId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT party_uuid FROM mmorpg_party_member WHERE player_uuid = ?")) {
                    statement.setBytes(1, bytes(playerId));
                    try (ResultSet row = statement.executeQuery()) {
                        if (!row.next()) return Optional.empty();
                        partyId = uuid(row.getBytes(1));
                    }
                }
                return read(connection, partyId, false);
            });
        } catch (SQLException failure) {
            throw storage("failed to find party for " + playerId, failure);
        }
    }

    @Override public PartySnapshot save(PartySnapshot party) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE mmorpg_party SET leader_uuid = ?, maximum_members = ?, "
                                + "reward_range = ?, rewards_same_world = ?, created_at = ?, "
                                + "party_revision = ? WHERE party_uuid = ? AND party_revision = ?")) {
                    bind(statement, party, 1);
                    statement.setBytes(7, bytes(party.partyId()));
                    statement.setLong(8, party.revision() - 1);
                    if (statement.executeUpdate() != 1) {
                        Optional<PartySnapshot> persisted = read(connection, party.partyId(), true);
                        if (persisted.isPresent()
                                && persisted.orElseThrow().revision() >= party.revision()) {
                            return persisted.orElseThrow();
                        }
                        throw new MMOException(ErrorCode.STORAGE_FAILURE,
                                "party optimistic revision conflict");
                    }
                }
                deleteChildren(connection, party.partyId());
                writeChildren(connection, party);
                return party;
            });
        } catch (SQLException failure) {
            throw storage("failed to save party " + party.partyId(), failure);
        }
    }

    @Override public boolean delete(UUID partyId, long expectedRevision) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM mmorpg_party WHERE party_uuid = ? AND party_revision = ?")) {
                    statement.setBytes(1, bytes(partyId));
                    statement.setLong(2, expectedRevision);
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException failure) {
            throw storage("failed to delete party " + partyId, failure);
        }
    }

    private static Optional<PartySnapshot> read(
            Connection connection, UUID partyId, boolean lock) throws SQLException {
        Header header;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT leader_uuid, maximum_members, reward_range, rewards_same_world, "
                        + "created_at, party_revision FROM mmorpg_party WHERE party_uuid = ?"
                        + (lock ? " FOR UPDATE" : ""))) {
            statement.setBytes(1, bytes(partyId));
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                header = new Header(uuid(row.getBytes(1)), row.getInt(2), row.getDouble(3),
                        row.getBoolean(4), row.getTimestamp(5).toInstant(), row.getLong(6));
            }
        }
        HashSet<UUID> members = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT player_uuid FROM mmorpg_party_member WHERE party_uuid = ?")) {
            statement.setBytes(1, bytes(partyId));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) members.add(uuid(rows.getBytes(1)));
            }
        }
        HashMap<UUID, java.time.Instant> invitations = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT player_uuid, expires_at FROM mmorpg_party_invitation "
                        + "WHERE party_uuid = ?")) {
            statement.setBytes(1, bytes(partyId));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) invitations.put(
                        uuid(rows.getBytes(1)), rows.getTimestamp(2).toInstant());
            }
        }
        return Optional.of(new PartySnapshot(partyId, header.leader(), members, invitations,
                header.maximum(), header.range(), header.sameWorld(),
                header.createdAt(), header.revision()));
    }

    private static void writeChildren(Connection connection, PartySnapshot party)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO mmorpg_party_member (party_uuid, player_uuid) VALUES (?, ?)")) {
            for (UUID member : party.members()) {
                statement.setBytes(1, bytes(party.partyId()));
                statement.setBytes(2, bytes(member));
                statement.addBatch();
            }
            statement.executeBatch();
        }
        if (party.invitations().isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO mmorpg_party_invitation "
                        + "(party_uuid, player_uuid, expires_at) VALUES (?, ?, ?)")) {
            for (var invitation : party.invitations().entrySet()) {
                statement.setBytes(1, bytes(party.partyId()));
                statement.setBytes(2, bytes(invitation.getKey()));
                statement.setTimestamp(3, Timestamp.from(invitation.getValue()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void deleteChildren(Connection connection, UUID partyId) throws SQLException {
        for (String table : java.util.List.of(
                "mmorpg_party_member", "mmorpg_party_invitation")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE party_uuid = ?")) {
                statement.setBytes(1, bytes(partyId));
                statement.executeUpdate();
            }
        }
    }

    private static void bind(PreparedStatement statement, PartySnapshot party, int start)
            throws SQLException {
        statement.setBytes(start, bytes(party.leaderId()));
        statement.setInt(start + 1, party.maximumMembers());
        statement.setDouble(start + 2, party.rewardRange());
        statement.setBoolean(start + 3, party.rewardsRequireSameWorld());
        statement.setTimestamp(start + 4, Timestamp.from(party.createdAt()));
        statement.setLong(start + 5, party.revision());
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
    private record Header(UUID leader, int maximum, double range, boolean sameWorld,
                          java.time.Instant createdAt, long revision) {
    }
}
