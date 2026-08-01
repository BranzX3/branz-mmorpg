package com.branz.mmorpg.persistence.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.migration.ClasspathMigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationErrorCode;
import com.branz.mmorpg.persistence.migration.PostgresMigrationRunner;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcDeathPouchRepositoryIntegrationTest {
    private EmbeddedPostgres postgres;
    private DataSource dataSource;
    private JdbcDeathPouchRepository repository;

    @BeforeAll
    void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().setPort(0).start();
        dataSource = postgres.getPostgresDatabase();
    }

    @AfterAll
    void stopPostgres() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
        Result<MigrationCatalog, MigrationErrorCode> loaded =
                ClasspathMigrationCatalog.loadDefault();
        assertTrue(loaded.isSuccess());
        assertTrue(
                new PostgresMigrationRunner(dataSource)
                        .migrate(
                                ((Result.Success<MigrationCatalog, MigrationErrorCode>) loaded)
                                        .value())
                        .isSuccess());
        repository = new JdbcDeathPouchRepository(dataSource);
    }

    @Test
    void intentionActivationRecoveryAndExactReplayAreMonotonic() throws Exception {
        Identity identity = identity(Instant.parse("2026-08-01T00:00:00Z"));
        DeathPouchCommit pending = commit(identity, DeathPouchState.PENDING_DEBIT, 0);
        TransactionRequest pendingRequest = request(identity, "create", 0);
        DeathPouchCommitExecution created = success(repository.commit(pendingRequest, pending));
        assertEquals(DeathPouchState.PENDING_DEBIT, created.record().state());
        assertFalse(created.transaction().replayed());

        DeathPouchCommitExecution replayed = success(repository.commit(pendingRequest, pending));
        assertTrue(replayed.transaction().replayed());
        assertEquals(1, replayed.record().version());
        assertEquals(1, success(repository.findRecoverable()).size());

        DeathPouchRecord active =
                success(
                                repository.commit(
                                        request(identity, "active", 1),
                                        commit(identity, DeathPouchState.ACTIVE, 1)))
                        .record();
        assertEquals(2, active.version());
        assertEquals(1, success(repository.findActive(identity.owner())).size());
        assertTrue(success(repository.findRecoverable()).isEmpty());

        DeathPouchRecord recovering =
                success(
                                repository.commit(
                                        request(identity, "recovering", 2),
                                        commit(identity, DeathPouchState.RECOVERING, 2)))
                        .record();
        assertEquals(1, success(repository.findRecoverable()).size());
        DeathPouchRecord recovered =
                success(
                                repository.commit(
                                        request(identity, "recovered", 3),
                                        commit(identity, DeathPouchState.RECOVERED, 3)))
                        .record();
        assertEquals(4, recovered.version());
        assertTrue(success(repository.findActive(identity.owner())).isEmpty());
        assertTrue(success(repository.findRecoverable()).isEmpty());

        Result<DeathPouchCommitExecution, TransactionErrorCode> backward =
                repository.commit(
                        request(identity, "illegal-active", 4),
                        commit(identity, DeathPouchState.ACTIVE, 4));
        assertFalse(backward.isSuccess());
        assertEquals(
                TransactionErrorCode.VALUE_STALE_VERSION,
                ((Result.Failure<DeathPouchCommitExecution, TransactionErrorCode>) backward)
                        .error());
        assertEquals(4, auditCount());
    }

    @Test
    void expiryQueryAndTerminalExpiryAreStable() {
        Identity identity = identity(Instant.parse("2026-07-01T00:00:00Z"));
        success(
                repository.commit(
                        request(identity, "create", 0),
                        commit(identity, DeathPouchState.PENDING_DEBIT, 0)));
        DeathPouchRecord active =
                success(
                                repository.commit(
                                        request(identity, "active", 1),
                                        commit(identity, DeathPouchState.ACTIVE, 1)))
                        .record();
        assertTrue(
                success(repository.findExpirable(identity.expiresAt().minusSeconds(1))).isEmpty());
        assertEquals(1, success(repository.findExpirable(identity.expiresAt())).size());
        DeathPouchRecord expired =
                success(
                                repository.commit(
                                        request(identity, "expired", active.version()),
                                        commit(
                                                identity,
                                                DeathPouchState.EXPIRED,
                                                active.version())))
                        .record();
        assertEquals(DeathPouchState.EXPIRED, expired.state());
        assertTrue(
                success(repository.findExpirable(identity.expiresAt().plusSeconds(1))).isEmpty());
    }

    private Identity identity(Instant createdAt) {
        return new Identity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new CharacterId(UUID.randomUUID()),
                100,
                UUID.randomUUID(),
                UUID.randomUUID(),
                createdAt,
                createdAt.plusSeconds(7 * 24 * 60 * 60));
    }

    private static DeathPouchCommit commit(
            Identity identity, DeathPouchState state, long expectedVersion) {
        return new DeathPouchCommit(
                identity.pouchId(),
                identity.deathId(),
                identity.owner(),
                identity.amount(),
                identity.debitId(),
                identity.creditId(),
                "minecraft:overworld",
                10.5,
                64,
                -3.25,
                identity.createdAt(),
                identity.expiresAt(),
                state,
                expectedVersion,
                "{\"state\":\"" + state + "\"}");
    }

    private static TransactionRequest request(
            Identity identity, String action, long expectedVersion) {
        UUID transactionId = UUID.randomUUID();
        return TransactionRequest.system(
                new TransactionId(transactionId),
                "death-pouch:" + identity.pouchId() + ":" + action,
                JdbcDeathPouchRepository.DEATH_POUCH_COMMIT,
                "{\"expectedVersion\":" + expectedVersion + "}",
                "{\"state\":\"" + action + "\"}",
                "test-v1");
    }

    private int auditCount() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet row =
                        statement.executeQuery(
                                "SELECT COUNT(*) FROM audit_log WHERE subject_type = 'DEATH_POUCH'")) {
            row.next();
            return row.getInt(1);
        }
    }

    private static <T> T success(Result<T, TransactionErrorCode> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<T, TransactionErrorCode>) result).value();
    }

    private record Identity(
            UUID pouchId,
            UUID deathId,
            CharacterId owner,
            long amount,
            UUID debitId,
            UUID creditId,
            Instant createdAt,
            Instant expiresAt) {}
}
