package com.branz.mmorpg.persistence.lease;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.SessionId;
import com.branz.mmorpg.api.result.Result;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/** Blocking PostgreSQL repository; callers must keep it off Paper server threads. */
public final class JdbcCharacterLeaseRepository implements CharacterLeaseRepository {
    private static final String COLUMNS =
            """
            character_id, server_instance, session_id, version,
            acquired_at, heartbeat_at, expires_at
            """;

    private final DataSource dataSource;

    public JdbcCharacterLeaseRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Result<LeaseAcquireOutcome, LeaseErrorCode> acquire(
            CharacterId characterId,
            ServerInstanceId serverInstanceId,
            SessionId sessionId,
            Duration timeToLive) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(serverInstanceId, "serverInstanceId");
        Objects.requireNonNull(sessionId, "sessionId");
        long ttlMillis = ttlMillis(timeToLive);
        return inTransaction(
                connection -> {
                    CharacterLease inserted =
                            insert(connection, characterId, serverInstanceId, sessionId, ttlMillis);
                    if (inserted != null) {
                        return Result.success(new LeaseAcquireOutcome.Acquired(inserted));
                    }
                    LockedLease existing = selectForUpdate(connection, characterId);
                    if (existing == null) {
                        return Result.failure(
                                LeaseErrorCode.LEASE_DATABASE_UNAVAILABLE,
                                "Lease conflict row disappeared during acquisition.");
                    }
                    if (existing.expired()) {
                        return Result.success(
                                new LeaseAcquireOutcome.RecoveryRequired(existing.lease()));
                    }
                    if (sameOwner(existing.lease(), serverInstanceId, sessionId)) {
                        return Result.success(
                                new LeaseAcquireOutcome.AlreadyHeld(existing.lease()));
                    }
                    return Result.success(new LeaseAcquireOutcome.Conflict(existing.lease()));
                });
    }

    @Override
    public Result<CharacterLease, LeaseErrorCode> recoverExpired(
            CharacterId characterId,
            long expectedVersion,
            ServerInstanceId serverInstanceId,
            SessionId sessionId,
            Duration timeToLive) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(serverInstanceId, "serverInstanceId");
        Objects.requireNonNull(sessionId, "sessionId");
        requireVersion(expectedVersion);
        long ttlMillis = ttlMillis(timeToLive);
        return inTransaction(
                connection -> {
                    LockedLease current = selectForUpdate(connection, characterId);
                    if (current == null) {
                        return Result.failure(
                                LeaseErrorCode.LEASE_NOT_FOUND, "Character lease does not exist.");
                    }
                    if (current.lease().version() != expectedVersion) {
                        return staleVersion();
                    }
                    if (!current.expired()) {
                        return Result.failure(
                                LeaseErrorCode.LEASE_NOT_EXPIRED,
                                "A live lease cannot be reassigned.");
                    }
                    return Result.success(
                            updateOwner(
                                    connection,
                                    current.lease(),
                                    serverInstanceId,
                                    sessionId,
                                    ttlMillis));
                });
    }

    @Override
    public Result<CharacterLease, LeaseErrorCode> heartbeat(
            CharacterId characterId,
            ServerInstanceId serverInstanceId,
            SessionId sessionId,
            long expectedVersion,
            Duration timeToLive) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(serverInstanceId, "serverInstanceId");
        Objects.requireNonNull(sessionId, "sessionId");
        requireVersion(expectedVersion);
        long ttlMillis = ttlMillis(timeToLive);
        return inTransaction(
                connection -> {
                    LockedLease current = selectForUpdate(connection, characterId);
                    if (current == null) {
                        return Result.failure(
                                LeaseErrorCode.LEASE_NOT_FOUND, "Character lease does not exist.");
                    }
                    if (!sameOwner(current.lease(), serverInstanceId, sessionId)) {
                        return ownershipMismatch();
                    }
                    if (current.lease().version() != expectedVersion) {
                        return staleVersion();
                    }
                    if (current.expired()) {
                        return Result.failure(
                                LeaseErrorCode.LEASE_EXPIRED,
                                "Expired leases require recovery before reuse.");
                    }
                    return Result.success(updateHeartbeat(connection, current.lease(), ttlMillis));
                });
    }

    @Override
    public Result<LeaseReleaseOutcome, LeaseErrorCode> release(
            CharacterId characterId,
            ServerInstanceId serverInstanceId,
            SessionId sessionId,
            long expectedVersion) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(serverInstanceId, "serverInstanceId");
        Objects.requireNonNull(sessionId, "sessionId");
        requireVersion(expectedVersion);
        return inTransaction(
                connection -> {
                    LockedLease current = selectForUpdate(connection, characterId);
                    if (current == null) {
                        return Result.success(LeaseReleaseOutcome.ALREADY_RELEASED);
                    }
                    if (!sameOwner(current.lease(), serverInstanceId, sessionId)) {
                        return ownershipMismatch();
                    }
                    if (current.lease().version() != expectedVersion) {
                        return staleVersion();
                    }
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "DELETE FROM character_leases WHERE character_id = ?")) {
                        statement.setObject(1, characterId.value());
                        statement.executeUpdate();
                    }
                    return Result.success(LeaseReleaseOutcome.RELEASED);
                });
    }

    @Override
    public Result<Optional<CharacterLease>, LeaseErrorCode> find(CharacterId characterId) {
        Objects.requireNonNull(characterId, "characterId");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT "
                                        + COLUMNS
                                        + " FROM character_leases "
                                        + "WHERE character_id = ?")) {
            statement.setObject(1, characterId.value());
            try (ResultSet row = statement.executeQuery()) {
                return Result.success(row.next() ? Optional.of(readLease(row)) : Optional.empty());
            }
        } catch (SQLException exception) {
            return databaseFailure(exception);
        }
    }

    private static CharacterLease insert(
            Connection connection,
            CharacterId characterId,
            ServerInstanceId serverInstanceId,
            SessionId sessionId,
            long ttlMillis)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO character_leases(
                            character_id, server_instance, session_id, version,
                            acquired_at, heartbeat_at, expires_at
                        )
                        VALUES (
                            ?, ?, ?, 1,
                            CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond')
                        )
                        ON CONFLICT (character_id) DO NOTHING
                        RETURNING
                        """
                                + COLUMNS)) {
            statement.setObject(1, characterId.value());
            statement.setString(2, serverInstanceId.value());
            statement.setObject(3, sessionId.value());
            statement.setLong(4, ttlMillis);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? readLease(row) : null;
            }
        }
    }

    private static LockedLease selectForUpdate(Connection connection, CharacterId characterId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT "
                                + COLUMNS
                                + ", expires_at <= CURRENT_TIMESTAMP AS expired "
                                + "FROM character_leases WHERE character_id = ? FOR UPDATE")) {
            statement.setObject(1, characterId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next()
                        ? new LockedLease(readLease(row), row.getBoolean("expired"))
                        : null;
            }
        }
    }

    private static CharacterLease updateOwner(
            Connection connection,
            CharacterLease current,
            ServerInstanceId serverInstanceId,
            SessionId sessionId,
            long ttlMillis)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        UPDATE character_leases
                        SET server_instance = ?,
                            session_id = ?,
                            version = version + 1,
                            acquired_at = CURRENT_TIMESTAMP,
                            heartbeat_at = CURRENT_TIMESTAMP,
                            expires_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond')
                        WHERE character_id = ?
                        RETURNING
                        """
                                + COLUMNS)) {
            statement.setString(1, serverInstanceId.value());
            statement.setObject(2, sessionId.value());
            statement.setLong(3, ttlMillis);
            statement.setObject(4, current.characterId().value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("Lease recovery update did not return a row.");
                }
                return readLease(row);
            }
        }
    }

    private static CharacterLease updateHeartbeat(
            Connection connection, CharacterLease current, long ttlMillis) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        UPDATE character_leases
                        SET version = version + 1,
                            heartbeat_at = CURRENT_TIMESTAMP,
                            expires_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond')
                        WHERE character_id = ?
                        RETURNING
                        """
                                + COLUMNS)) {
            statement.setLong(1, ttlMillis);
            statement.setObject(2, current.characterId().value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("Lease heartbeat update did not return a row.");
                }
                return readLease(row);
            }
        }
    }

    private <T> Result<T, LeaseErrorCode> inTransaction(TransactionWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                Result<T, LeaseErrorCode> result = work.execute(connection);
                if (result.isSuccess()) {
                    connection.commit();
                } else {
                    connection.rollback();
                }
                return result;
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                return databaseFailure(exception);
            } finally {
                restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (SQLException exception) {
            return databaseFailure(exception);
        }
    }

    private static CharacterLease readLease(ResultSet row) throws SQLException {
        return new CharacterLease(
                new CharacterId(row.getObject("character_id", java.util.UUID.class)),
                new ServerInstanceId(row.getString("server_instance")),
                new SessionId(row.getObject("session_id", java.util.UUID.class)),
                row.getLong("version"),
                instant(row, "acquired_at"),
                instant(row, "heartbeat_at"),
                instant(row, "expires_at"));
    }

    private static java.time.Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static boolean sameOwner(
            CharacterLease lease, ServerInstanceId serverInstanceId, SessionId sessionId) {
        return lease.serverInstanceId().equals(serverInstanceId)
                && lease.sessionId().equals(sessionId);
    }

    private static long ttlMillis(Duration timeToLive) {
        Objects.requireNonNull(timeToLive, "timeToLive");
        long millis = timeToLive.toMillis();
        if (millis < 1) {
            throw new IllegalArgumentException("lease time-to-live must be positive");
        }
        return millis;
    }

    private static void requireVersion(long version) {
        if (version < 1) {
            throw new IllegalArgumentException("expected version must be positive");
        }
    }

    private static <T> Result<T, LeaseErrorCode> ownershipMismatch() {
        return Result.failure(
                LeaseErrorCode.LEASE_OWNERSHIP_MISMATCH,
                "Lease belongs to another server session.");
    }

    private static <T> Result<T, LeaseErrorCode> staleVersion() {
        return Result.failure(
                LeaseErrorCode.LEASE_STALE_VERSION, "Lease version changed; reload before retry.");
    }

    private static <T> Result<T, LeaseErrorCode> databaseFailure(SQLException exception) {
        LeaseErrorCode code =
                "23505".equals(exception.getSQLState())
                        ? LeaseErrorCode.LEASE_SESSION_COLLISION
                        : LeaseErrorCode.LEASE_DATABASE_UNAVAILABLE;
        String state = exception.getSQLState();
        return Result.failure(
                code,
                exception.getClass().getSimpleName() + (state == null ? "" : " SQLSTATE=" + state));
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original failure.
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
            // Closing the pooled connection is the only safe recovery left.
        }
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        Result<T, LeaseErrorCode> execute(Connection connection) throws SQLException;
    }

    private record LockedLease(CharacterLease lease, boolean expired) {}
}
