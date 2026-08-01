package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.migration.ClasspathMigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationErrorCode;
import com.branz.mmorpg.persistence.migration.PostgresMigrationRunner;
import com.branz.mmorpg.persistence.transaction.JdbcBossEncounterStateRepository;
import com.branz.mmorpg.persistence.transaction.JdbcPersonalRewardGrantRepository;
import com.branz.mmorpg.persistence.transaction.JdbcValueTransactionService;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.persistence.transaction.PersonalRewardGrantRecord;
import com.branz.mmorpg.persistence.transaction.PersonalRewardGrantState;
import com.branz.mmorpg.persistence.transaction.ValueLocationType;
import com.branz.mmorpg.worldloop.encounter.BossEncounterEngine;
import com.branz.mmorpg.worldloop.encounter.BossEncounterRuntime;
import com.branz.mmorpg.worldloop.reward.EncounterRewardTable;
import com.branz.mmorpg.worldloop.reward.RewardContribution;
import com.branz.mmorpg.worldloop.reward.RewardEligibilityProfile;
import com.branz.mmorpg.worldloop.reward.RewardIneligibilityReason;
import com.branz.mmorpg.worldloop.reward.RewardTableEntry;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonalRewardReconciliationServiceIntegrationTest {
    private static final DefinitionId BOSS = DefinitionId.of("encounter.boss.training_golem");
    private EmbeddedPostgres postgres;
    private DataSource dataSource;

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
    }

    @Test
    void freezesRollsDeliversAndExactlyReplaysAWholeVictory() {
        JdbcBossEncounterStateRepository bossStates =
                new JdbcBossEncounterStateRepository(dataSource);
        JdbcPersonalRewardGrantRepository grants =
                new JdbcPersonalRewardGrantRepository(dataSource);
        JdbcValueTransactionService values = new JdbcValueTransactionService(dataSource);
        CharacterId eligible = new CharacterId(UUID.randomUUID());
        CharacterId inactive = new CharacterId(UUID.randomUUID());
        BossEncounterEngine engine = new BossEncounterEngine();
        BossEncounterRuntime runtime =
                success(
                        engine.start(
                                new EncounterId(UUID.randomUUID()),
                                BOSS,
                                UUID.randomUUID(),
                                List.of(eligible, inactive),
                                100));
        runtime =
                success(
                                engine.recordRewardContribution(
                                        runtime,
                                        eligible,
                                        new RewardContribution(100, 0, 0, 0),
                                        UUID.randomUUID(),
                                        120))
                        .runtime();
        runtime = success(engine.confirmVictory(runtime, UUID.randomUUID(), 130)).runtime();
        assertTrue(
                new DurableBossEncounterStore(bossStates, "test-v1")
                        .create(runtime, UUID.randomUUID())
                        .isSuccess());

        EncounterRewardTable table =
                new EncounterRewardTable(
                        BOSS,
                        new RewardEligibilityProfile(100, 50, 75, 1, 600),
                        0.20,
                        List.of(
                                new RewardTableEntry(
                                        DefinitionId.of("material.infusion_stock"), 1, 2, 2)));
        PersonalRewardReconciliationService service =
                new PersonalRewardReconciliationService(
                        grants, values, "test-v1", Map.of(BOSS, table));

        PersonalRewardReconciliation first = success(service.reconcile(runtime));
        PersonalRewardReconciliation replay = success(service.reconcile(runtime));
        assertEquals(first, replay);
        assertEquals(
                RewardIneligibilityReason.INSUFFICIENT_CONTRIBUTION,
                first.rejected().get(inactive));
        assertEquals(1, first.delivered().size());
        assertTrue(success(grants.findPending()).isEmpty());
        LotLocationRecord lot =
                success(values.findLot(first.delivered().get(eligible).lotId())).orElseThrow();
        assertEquals(eligible, lot.ownerCharacterId().orElseThrow());
        assertEquals(2, lot.quantity());
        assertEquals(ValueLocationType.PENDING_REWARDS, lot.location().type());
        assertEquals(
                PersonalRewardGrantState.DELIVERED,
                deliveredGrant(grants, runtime, eligible).state());
    }

    private static PersonalRewardGrantRecord deliveredGrant(
            JdbcPersonalRewardGrantRepository grants,
            BossEncounterRuntime runtime,
            CharacterId characterId) {
        UUID grantId =
                UUID.nameUUIDFromBytes(
                        ("personal-reward:"
                                        + runtime.encounterId().value()
                                        + ":"
                                        + runtime.attempt()
                                        + ":"
                                        + characterId.value())
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return success(grants.find(grantId)).orElseThrow();
    }

    private static <T, E extends com.branz.mmorpg.api.result.ErrorCode> T success(
            Result<T, E> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<T, E>) result).value();
    }
}
