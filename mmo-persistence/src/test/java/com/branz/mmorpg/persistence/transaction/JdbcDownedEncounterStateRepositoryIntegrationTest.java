package com.branz.mmorpg.persistence.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.EncounterId;
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
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcDownedEncounterStateRepositoryIntegrationTest {
    private static final DefinitionId BOSS = DefinitionId.of("encounter.boss.training_golem");

    private EmbeddedPostgres postgres;
    private DataSource dataSource;
    private JdbcBossEncounterStateRepository bossRepository;
    private JdbcDownedEncounterStateRepository repository;

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
        bossRepository = new JdbcBossEncounterStateRepository(dataSource);
        repository = new JdbcDownedEncounterStateRepository(dataSource);
    }

    @Test
    void createsReplaysAdvancesAndClosesRecoverableState() throws Exception {
        EncounterId encounterId = createBoss();
        String downed = "{\"schemaVersion\":1,\"lifeState\":\"DOWNED\"}";
        UUID operation = UUID.randomUUID();
        TransactionRequest createRequest = request(operation, encounterId, 0, downed);
        DownedEncounterStateCommit create =
                new DownedEncounterStateCommit(encounterId, 1, true, 0, downed);

        DownedEncounterStateCommitExecution created =
                success(repository.commit(createRequest, create));
        assertEquals(1, created.record().version());
        assertFalse(created.transaction().replayed());
        assertTrue(created.record().recoverable());

        DownedEncounterStateCommitExecution replayed =
                success(repository.commit(createRequest, create));
        assertTrue(replayed.transaction().replayed());
        assertEquals(1, replayed.record().version());

        String nextAttempt = "{\"schemaVersion\":1,\"lifeState\":\"ACTIVE\"}";
        DownedEncounterStateCommitExecution advanced =
                success(
                        repository.commit(
                                request(UUID.randomUUID(), encounterId, 1, nextAttempt),
                                new DownedEncounterStateCommit(
                                        encounterId, 2, true, 1, nextAttempt)));
        assertEquals(2, advanced.record().attempt());
        assertEquals(
                List.of(encounterId),
                success(repository.findRecoverable()).stream()
                        .map(DownedEncounterStateRecord::encounterId)
                        .toList());

        success(
                repository.commit(
                        request(UUID.randomUUID(), encounterId, 2, nextAttempt),
                        new DownedEncounterStateCommit(encounterId, 2, false, 2, nextAttempt)));
        assertTrue(success(repository.findRecoverable()).isEmpty());
        assertEquals(3, success(repository.find(encounterId)).orElseThrow().version());
        assertEquals(4, scalarInt("SELECT COUNT(*) FROM transaction_journal"));
        assertEquals(4, scalarInt("SELECT COUNT(*) FROM audit_log"));
    }

    @Test
    void staleVersionAndMismatchedReplayFailWithoutMutation() {
        EncounterId encounterId = createBoss();
        String payload = "{\"schemaVersion\":1,\"lifeState\":\"ACTIVE\"}";
        success(
                repository.commit(
                        request(UUID.randomUUID(), encounterId, 0, payload),
                        new DownedEncounterStateCommit(encounterId, 1, true, 0, payload)));

        Result<DownedEncounterStateCommitExecution, TransactionErrorCode> stale =
                repository.commit(
                        request(UUID.randomUUID(), encounterId, 0, payload),
                        new DownedEncounterStateCommit(encounterId, 1, true, 0, payload));
        assertFailure(TransactionErrorCode.VALUE_STALE_VERSION, stale);

        UUID operation = UUID.randomUUID();
        TransactionRequest firstRequest = request(operation, encounterId, 1, payload);
        success(
                repository.commit(
                        firstRequest,
                        new DownedEncounterStateCommit(encounterId, 1, true, 1, payload)));
        TransactionRequest conflicting =
                TransactionRequest.system(
                        new TransactionId(operation),
                        "downed:" + encounterId.value() + ":" + operation,
                        JdbcDownedEncounterStateRepository.DOWNED_ENCOUNTER_STATE_COMMIT,
                        "{\"expectedVersion\":2}",
                        "{\"different\":true}",
                        "test-content-v1");
        assertFailure(
                TransactionErrorCode.TRANSACTION_IDEMPOTENCY_CONFLICT,
                repository.commit(
                        conflicting,
                        new DownedEncounterStateCommit(
                                encounterId, 1, false, 2, "{\"different\":true}")));
    }

    private EncounterId createBoss() {
        EncounterId encounterId = new EncounterId(UUID.randomUUID());
        UUID operation = UUID.randomUUID();
        String payload = "{\"schemaVersion\":1,\"phase\":\"ACTIVE\"}";
        TransactionRequest request =
                TransactionRequest.system(
                        new TransactionId(operation),
                        "encounter:" + encounterId.value() + ":" + operation,
                        JdbcBossEncounterStateRepository.BOSS_ENCOUNTER_STATE_COMMIT,
                        "{\"expectedVersion\":0}",
                        payload,
                        "test-content-v1");
        success(
                bossRepository.commit(
                        request,
                        new BossEncounterStateCommit(encounterId, BOSS, "ACTIVE", 0, payload)));
        return encounterId;
    }

    private static TransactionRequest request(
            UUID operationId, EncounterId encounterId, long expectedVersion, String payload) {
        return TransactionRequest.system(
                new TransactionId(operationId),
                "downed:" + encounterId.value() + ":" + operationId,
                JdbcDownedEncounterStateRepository.DOWNED_ENCOUNTER_STATE_COMMIT,
                "{\"expectedVersion\":" + expectedVersion + "}",
                payload,
                "test-content-v1");
    }

    private int scalarInt(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery(sql)) {
            row.next();
            return row.getInt(1);
        }
    }

    private static <T> T success(Result<T, TransactionErrorCode> result) {
        assertTrue(
                result.isSuccess(),
                () ->
                        result instanceof Result.Failure<T, TransactionErrorCode> failure
                                ? failure.error().code() + ": " + failure.detail()
                                : "");
        return ((Result.Success<T, TransactionErrorCode>) result).value();
    }

    private static void assertFailure(
            TransactionErrorCode expected, Result<?, TransactionErrorCode> result) {
        assertFalse(result.isSuccess());
        assertEquals(expected, ((Result.Failure<?, TransactionErrorCode>) result).error());
    }
}
