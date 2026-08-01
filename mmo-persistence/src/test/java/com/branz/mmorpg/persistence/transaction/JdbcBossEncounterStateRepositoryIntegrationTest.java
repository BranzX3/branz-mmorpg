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
class JdbcBossEncounterStateRepositoryIntegrationTest {
    private static final DefinitionId BOSS = DefinitionId.of("encounter.boss.training_golem");

    private EmbeddedPostgres postgres;
    private DataSource dataSource;
    private JdbcBossEncounterStateRepository repository;

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
        repository = new JdbcBossEncounterStateRepository(dataSource);
    }

    @Test
    void createsReplaysUpdatesAndFiltersRecoverableEncounters() throws Exception {
        EncounterId encounterId = new EncounterId(UUID.randomUUID());
        String activePayload = "{\"schemaVersion\":1,\"phase\":\"ACTIVE\",\"attempt\":1}";
        UUID startOperation = UUID.randomUUID();
        TransactionRequest startRequest = request(startOperation, encounterId, 0, activePayload);
        BossEncounterStateCommit start =
                new BossEncounterStateCommit(encounterId, BOSS, "ACTIVE", 0, activePayload);

        BossEncounterStateCommitExecution created = success(repository.commit(startRequest, start));
        assertEquals(1, created.record().version());
        assertFalse(created.transaction().replayed());

        BossEncounterStateCommitExecution replayed =
                success(repository.commit(startRequest, start));
        assertTrue(replayed.transaction().replayed());
        assertEquals(1, replayed.record().version());

        String resettingPayload = "{\"schemaVersion\":1,\"phase\":\"RESETTING\",\"attempt\":1}";
        BossEncounterStateCommitExecution resetting =
                success(
                        repository.commit(
                                request(UUID.randomUUID(), encounterId, 1, resettingPayload),
                                new BossEncounterStateCommit(
                                        encounterId, BOSS, "RESETTING", 1, resettingPayload)));
        assertEquals(2, resetting.record().version());
        List<BossEncounterStateRecord> recoverable = success(repository.findRecoverable());
        assertEquals(
                List.of(encounterId),
                recoverable.stream().map(BossEncounterStateRecord::encounterId).toList());

        String completedPayload = "{\"schemaVersion\":1,\"phase\":\"COMPLETED\",\"attempt\":1}";
        success(
                repository.commit(
                        request(UUID.randomUUID(), encounterId, 2, completedPayload),
                        new BossEncounterStateCommit(
                                encounterId, BOSS, "COMPLETED", 2, completedPayload)));
        assertTrue(success(repository.findRecoverable()).isEmpty());
        assertEquals(3, success(repository.find(encounterId)).orElseThrow().version());
        assertEquals(3, scalarInt("SELECT COUNT(*) FROM transaction_journal"));
        assertEquals(3, scalarInt("SELECT COUNT(*) FROM audit_log"));
    }

    @Test
    void staleVersionAndMismatchedReplayFailWithoutMutation() {
        EncounterId encounterId = new EncounterId(UUID.randomUUID());
        String payload = "{\"schemaVersion\":1,\"phase\":\"ACTIVE\"}";
        success(
                repository.commit(
                        request(UUID.randomUUID(), encounterId, 0, payload),
                        new BossEncounterStateCommit(encounterId, BOSS, "ACTIVE", 0, payload)));

        Result<BossEncounterStateCommitExecution, TransactionErrorCode> stale =
                repository.commit(
                        request(UUID.randomUUID(), encounterId, 0, payload),
                        new BossEncounterStateCommit(encounterId, BOSS, "ACTIVE", 0, payload));
        assertFailure(TransactionErrorCode.VALUE_STALE_VERSION, stale);

        UUID operation = UUID.randomUUID();
        TransactionRequest firstRequest = request(operation, encounterId, 1, payload);
        success(
                repository.commit(
                        firstRequest,
                        new BossEncounterStateCommit(encounterId, BOSS, "ACTIVE", 1, payload)));
        TransactionRequest conflicting =
                TransactionRequest.system(
                        new TransactionId(operation),
                        "encounter:" + encounterId.value() + ":" + operation,
                        JdbcBossEncounterStateRepository.BOSS_ENCOUNTER_STATE_COMMIT,
                        "{\"expectedVersion\":2}",
                        "{\"different\":true}",
                        "test-content-v1");
        assertFailure(
                TransactionErrorCode.TRANSACTION_IDEMPOTENCY_CONFLICT,
                repository.commit(
                        conflicting,
                        new BossEncounterStateCommit(
                                encounterId, BOSS, "VICTORY_PENDING", 2, "{\"different\":true}")));
    }

    private static TransactionRequest request(
            UUID operationId, EncounterId encounterId, long expectedVersion, String payload) {
        return TransactionRequest.system(
                new TransactionId(operationId),
                "encounter:" + encounterId.value() + ":" + operationId,
                JdbcBossEncounterStateRepository.BOSS_ENCOUNTER_STATE_COMMIT,
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
