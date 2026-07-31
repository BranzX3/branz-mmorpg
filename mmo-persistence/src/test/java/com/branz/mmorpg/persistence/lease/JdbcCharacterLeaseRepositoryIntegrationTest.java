package com.branz.mmorpg.persistence.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.SessionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.migration.ClasspathMigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationErrorCode;
import com.branz.mmorpg.persistence.migration.PostgresMigrationRunner;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcCharacterLeaseRepositoryIntegrationTest {
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final ServerInstanceId SERVER_A = new ServerInstanceId("server-a");
    private static final ServerInstanceId SERVER_B = new ServerInstanceId("server-b");

    private EmbeddedPostgres postgres;
    private DataSource dataSource;
    private JdbcCharacterLeaseRepository repository;

    @BeforeAll
    void startPostgresAndMigrate() throws Exception {
        postgres = EmbeddedPostgres.builder().setPort(0).start();
        dataSource = postgres.getPostgresDatabase();
        Result<MigrationCatalog, MigrationErrorCode> loaded =
                ClasspathMigrationCatalog.loadDefault();
        assertTrue(loaded.isSuccess());
        MigrationCatalog catalog =
                ((Result.Success<MigrationCatalog, MigrationErrorCode>) loaded).value();
        assertTrue(new PostgresMigrationRunner(dataSource).migrate(catalog).isSuccess());
        repository = new JdbcCharacterLeaseRepository(dataSource);
    }

    @AfterAll
    void stopPostgres() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void clearLeases() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("TRUNCATE TABLE character_leases");
        }
    }

    @Test
    void acquireIsIdempotentForTheSameSessionAndRejectsALiveCompetitor() {
        CharacterId characterId = characterId();
        SessionId sessionA = sessionId();

        LeaseAcquireOutcome first = acquire(characterId, SERVER_A, sessionA);
        LeaseAcquireOutcome retry = acquire(characterId, SERVER_A, sessionA);
        LeaseAcquireOutcome conflict = acquire(characterId, SERVER_B, sessionId());

        assertInstanceOf(LeaseAcquireOutcome.Acquired.class, first);
        assertInstanceOf(LeaseAcquireOutcome.AlreadyHeld.class, retry);
        assertInstanceOf(LeaseAcquireOutcome.Conflict.class, conflict);
        assertEquals(first.lease(), retry.lease());
        assertEquals(sessionA, conflict.lease().sessionId());
    }

    @Test
    void expiredLeaseRequiresVersionedRecoveryBeforeReassignment() throws Exception {
        CharacterId characterId = characterId();
        SessionId oldSession = sessionId();
        CharacterLease acquired = acquire(characterId, SERVER_A, oldSession).lease();
        expire(characterId);
        SessionId replacementSession = sessionId();

        LeaseAcquireOutcome inspection = acquire(characterId, SERVER_B, replacementSession);
        assertInstanceOf(LeaseAcquireOutcome.RecoveryRequired.class, inspection);

        Result<CharacterLease, LeaseErrorCode> staleRecovery =
                repository.recoverExpired(
                        characterId, acquired.version() + 1, SERVER_B, replacementSession, TTL);
        assertEquals(LeaseErrorCode.LEASE_STALE_VERSION, failure(staleRecovery));

        CharacterLease recovered =
                success(
                        repository.recoverExpired(
                                characterId,
                                acquired.version(),
                                SERVER_B,
                                replacementSession,
                                TTL));
        assertEquals(acquired.version() + 1, recovered.version());
        assertEquals(SERVER_B, recovered.serverInstanceId());
        assertEquals(replacementSession, recovered.sessionId());

        Result<CharacterLease, LeaseErrorCode> oldOwnerHeartbeat =
                repository.heartbeat(characterId, SERVER_A, oldSession, acquired.version(), TTL);
        assertEquals(LeaseErrorCode.LEASE_OWNERSHIP_MISMATCH, failure(oldOwnerHeartbeat));
    }

    @Test
    void heartbeatUsesOptimisticVersionAndCannotReviveAnExpiredLease() throws Exception {
        CharacterId characterId = characterId();
        SessionId sessionId = sessionId();
        CharacterLease acquired = acquire(characterId, SERVER_A, sessionId).lease();

        CharacterLease heartbeat =
                success(
                        repository.heartbeat(
                                characterId, SERVER_A, sessionId, acquired.version(), TTL));
        assertEquals(acquired.version() + 1, heartbeat.version());

        Result<CharacterLease, LeaseErrorCode> stale =
                repository.heartbeat(characterId, SERVER_A, sessionId, acquired.version(), TTL);
        assertEquals(LeaseErrorCode.LEASE_STALE_VERSION, failure(stale));

        expire(characterId);
        Result<CharacterLease, LeaseErrorCode> expired =
                repository.heartbeat(characterId, SERVER_A, sessionId, heartbeat.version(), TTL);
        assertEquals(LeaseErrorCode.LEASE_EXPIRED, failure(expired));
    }

    @Test
    void releaseChecksOwnershipAndVersionThenBecomesIdempotent() {
        CharacterId characterId = characterId();
        SessionId sessionId = sessionId();
        CharacterLease acquired = acquire(characterId, SERVER_A, sessionId).lease();

        Result<LeaseReleaseOutcome, LeaseErrorCode> wrongOwner =
                repository.release(characterId, SERVER_B, sessionId, acquired.version());
        assertEquals(LeaseErrorCode.LEASE_OWNERSHIP_MISMATCH, failure(wrongOwner));

        assertEquals(
                LeaseReleaseOutcome.RELEASED,
                success(repository.release(characterId, SERVER_A, sessionId, acquired.version())));
        assertEquals(
                LeaseReleaseOutcome.ALREADY_RELEASED,
                success(repository.release(characterId, SERVER_A, sessionId, acquired.version())));
        assertEquals(java.util.Optional.empty(), success(repository.find(characterId)));
    }

    @Test
    void concurrentAcquireProducesOneOwnerAndSessionIdsCannotBeReused() {
        CharacterId characterId = characterId();
        SessionId sessionA = sessionId();
        SessionId sessionB = sessionId();

        CompletableFuture<LeaseAcquireOutcome> first =
                CompletableFuture.supplyAsync(() -> acquire(characterId, SERVER_A, sessionA));
        CompletableFuture<LeaseAcquireOutcome> second =
                CompletableFuture.supplyAsync(() -> acquire(characterId, SERVER_B, sessionB));
        List<LeaseAcquireOutcome> outcomes = List.of(first.join(), second.join());

        assertEquals(
                1,
                outcomes.stream().filter(LeaseAcquireOutcome.Acquired.class::isInstance).count());
        assertEquals(
                1,
                outcomes.stream().filter(LeaseAcquireOutcome.Conflict.class::isInstance).count());

        CharacterId anotherCharacter = characterId();
        Result<LeaseAcquireOutcome, LeaseErrorCode> collision =
                repository.acquire(
                        anotherCharacter, SERVER_A, outcomes.getFirst().lease().sessionId(), TTL);
        assertFalse(collision.isSuccess());
        assertEquals(LeaseErrorCode.LEASE_SESSION_COLLISION, failure(collision));
    }

    private LeaseAcquireOutcome acquire(
            CharacterId characterId, ServerInstanceId serverInstanceId, SessionId sessionId) {
        return success(repository.acquire(characterId, serverInstanceId, sessionId, TTL));
    }

    private void expire(CharacterId characterId) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                UPDATE character_leases
                                SET heartbeat_at = CURRENT_TIMESTAMP - INTERVAL '2 seconds',
                                    expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                                WHERE character_id = ?
                                """)) {
            statement.setObject(1, characterId.value());
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static CharacterId characterId() {
        return new CharacterId(UUID.randomUUID());
    }

    private static SessionId sessionId() {
        return new SessionId(UUID.randomUUID());
    }

    private static <T> T success(Result<T, LeaseErrorCode> result) {
        assertTrue(result.isSuccess(), () -> failureDetail(result));
        return ((Result.Success<T, LeaseErrorCode>) result).value();
    }

    private static <T> LeaseErrorCode failure(Result<T, LeaseErrorCode> result) {
        assertFalse(result.isSuccess());
        return ((Result.Failure<T, LeaseErrorCode>) result).error();
    }

    private static <T> String failureDetail(Result<T, LeaseErrorCode> result) {
        if (result instanceof Result.Failure<T, LeaseErrorCode> failure) {
            return failure.error() + ": " + failure.detail();
        }
        return "";
    }
}
