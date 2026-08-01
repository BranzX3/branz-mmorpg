package com.branz.mmorpg.persistence.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
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
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcPersonalRewardGrantRepositoryIntegrationTest {
    private static final DefinitionId BOSS = DefinitionId.of("encounter.boss.training_golem");

    private EmbeddedPostgres postgres;
    private DataSource dataSource;
    private JdbcBossEncounterStateRepository bossRepository;
    private JdbcPersonalRewardGrantRepository repository;

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
        repository = new JdbcPersonalRewardGrantRepository(dataSource);
    }

    @Test
    void freezesReplaysRollsDeliversAndLeavesNoPendingGrant() throws Exception {
        EncounterId encounterId = createBoss();
        CharacterId characterId = character();
        UUID grantId = UUID.randomUUID();
        String frozen = "{\"schemaVersion\":1,\"eligibility\":\"ELIGIBLE\"}";
        PersonalRewardGrantCommit create =
                commit(
                        grantId,
                        encounterId,
                        characterId,
                        PersonalRewardGrantState.FROZEN,
                        0,
                        frozen);
        TransactionRequest createRequest = request(UUID.randomUUID(), grantId, 0, frozen);

        PersonalRewardGrantCommitExecution created =
                success(repository.commit(createRequest, create));
        assertEquals(1, created.record().version());
        assertFalse(created.transaction().replayed());
        assertEquals(PersonalRewardGrantState.FROZEN, created.record().state());

        PersonalRewardGrantCommitExecution replayed =
                success(repository.commit(createRequest, create));
        assertTrue(replayed.transaction().replayed());
        assertEquals(1, replayed.record().version());

        String rolled = "{\"schemaVersion\":1,\"reward\":\"lot.ember\"}";
        PersonalRewardGrantRecord rolledRecord =
                success(
                                repository.commit(
                                        request(UUID.randomUUID(), grantId, 1, rolled),
                                        commit(
                                                grantId,
                                                encounterId,
                                                characterId,
                                                PersonalRewardGrantState.ROLLED,
                                                1,
                                                rolled)))
                        .record();
        assertEquals(PersonalRewardGrantState.ROLLED, rolledRecord.state());
        assertEquals(1, success(repository.findPending()).size());

        PersonalRewardGrantRecord delivered =
                success(
                                repository.commit(
                                        request(UUID.randomUUID(), grantId, 2, rolled),
                                        commit(
                                                grantId,
                                                encounterId,
                                                characterId,
                                                PersonalRewardGrantState.DELIVERED,
                                                2,
                                                rolled)))
                        .record();
        assertEquals(PersonalRewardGrantState.DELIVERED, delivered.state());
        assertTrue(success(repository.findPending()).isEmpty());
        assertEquals(4, scalarInt("SELECT COUNT(*) FROM transaction_journal"));
        assertEquals(4, scalarInt("SELECT COUNT(*) FROM audit_log"));
    }

    @Test
    void uniquenessStaleBackwardAndConflictingReplayFailWithoutMutation() {
        EncounterId encounterId = createBoss();
        CharacterId characterId = character();
        UUID grantId = UUID.randomUUID();
        String frozen = "{\"schemaVersion\":1,\"state\":\"frozen\"}";
        success(
                repository.commit(
                        request(UUID.randomUUID(), grantId, 0, frozen),
                        commit(
                                grantId,
                                encounterId,
                                characterId,
                                PersonalRewardGrantState.FROZEN,
                                0,
                                frozen)));

        UUID duplicateGrantId = UUID.randomUUID();
        assertFailure(
                TransactionErrorCode.VALUE_STALE_VERSION,
                repository.commit(
                        request(UUID.randomUUID(), duplicateGrantId, 0, frozen),
                        commit(
                                duplicateGrantId,
                                encounterId,
                                characterId,
                                PersonalRewardGrantState.FROZEN,
                                0,
                                frozen)));

        String rolled = "{\"schemaVersion\":1,\"state\":\"rolled\"}";
        success(
                repository.commit(
                        request(UUID.randomUUID(), grantId, 1, rolled),
                        commit(
                                grantId,
                                encounterId,
                                characterId,
                                PersonalRewardGrantState.ROLLED,
                                1,
                                rolled)));
        assertFailure(
                TransactionErrorCode.VALUE_STALE_VERSION,
                repository.commit(
                        request(UUID.randomUUID(), grantId, 1, frozen),
                        commit(
                                grantId,
                                encounterId,
                                characterId,
                                PersonalRewardGrantState.FROZEN,
                                2,
                                frozen)));

        UUID operationId = UUID.randomUUID();
        TransactionRequest deliveredRequest = request(operationId, grantId, 2, rolled);
        success(
                repository.commit(
                        deliveredRequest,
                        commit(
                                grantId,
                                encounterId,
                                characterId,
                                PersonalRewardGrantState.DELIVERED,
                                2,
                                rolled)));
        TransactionRequest conflicting =
                TransactionRequest.system(
                        new TransactionId(operationId),
                        "reward-grant:" + grantId + ":" + operationId,
                        JdbcPersonalRewardGrantRepository.PERSONAL_REWARD_GRANT_COMMIT,
                        "{\"expectedVersion\":3}",
                        "{\"different\":true}",
                        "test-content-v1");
        assertFailure(
                TransactionErrorCode.TRANSACTION_IDEMPOTENCY_CONFLICT,
                repository.commit(
                        conflicting,
                        commit(
                                grantId,
                                encounterId,
                                characterId,
                                PersonalRewardGrantState.DELIVERED,
                                3,
                                "{\"different\":true}")));
        assertEquals(
                PersonalRewardGrantState.DELIVERED,
                success(repository.find(grantId)).orElseThrow().state());
    }

    private EncounterId createBoss() {
        EncounterId encounterId = new EncounterId(UUID.randomUUID());
        UUID operation = UUID.randomUUID();
        String payload = "{\"schemaVersion\":1,\"phase\":\"VICTORY_PENDING\"}";
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
                        new BossEncounterStateCommit(
                                encounterId, BOSS, "VICTORY_PENDING", 0, payload)));
        return encounterId;
    }

    private static PersonalRewardGrantCommit commit(
            UUID grantId,
            EncounterId encounterId,
            CharacterId characterId,
            PersonalRewardGrantState state,
            long version,
            String payload) {
        return new PersonalRewardGrantCommit(
                grantId, encounterId, 1, characterId, 77, state, version, payload);
    }

    private static TransactionRequest request(
            UUID operationId, UUID grantId, long expectedVersion, String payload) {
        return TransactionRequest.system(
                new TransactionId(operationId),
                "reward-grant:" + grantId + ":" + operationId,
                JdbcPersonalRewardGrantRepository.PERSONAL_REWARD_GRANT_COMMIT,
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

    private static CharacterId character() {
        return new CharacterId(UUID.randomUUID());
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
