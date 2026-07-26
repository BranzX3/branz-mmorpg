package com.branz.mmorpg.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.crafting.CraftFinalizeCommit;
import com.branz.mmorpg.api.crafting.CraftJob;
import com.branz.mmorpg.api.crafting.CraftPrepareCommit;
import com.branz.mmorpg.api.crafting.CraftingRepository;
import com.branz.mmorpg.api.crafting.ProfessionDefinition;
import com.branz.mmorpg.api.crafting.ProfessionSnapshot;
import com.branz.mmorpg.api.crafting.RecipeDefinition;
import com.branz.mmorpg.api.economy.EconomyPaymentPort;
import com.branz.mmorpg.api.gathering.GatheringNodeDefinition;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.item.LootDefinition;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.player.DuplicateLoginPolicy;
import com.branz.mmorpg.api.skill.SkillDefinition;
import com.branz.mmorpg.core.fixture.DirectScheduler;
import com.branz.mmorpg.core.fixture.FakePlayerProfileRepository;
import com.branz.mmorpg.core.fixture.FixedGameClock;
import com.branz.mmorpg.core.player.PlayerSessionService;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class DefaultCraftingServiceTest {
    private static final UUID PLAYER =
            UUID.fromString("9a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");
    private static final ContentId ORE = ContentId.parse("branz:aether_ore");
    private static final ContentId INGOT = ContentId.parse("branz:aether_ingot");
    private static final ContentId PROFESSION = ContentId.parse("branz:blacksmithing");
    private static final ContentId RECIPE = ContentId.parse("branz:aether_ingot_recipe");
    private static final Instant NOW = Instant.parse("2026-07-26T00:00:00Z");

    @Test
    void finalizationFailureRollsBackAndRetryNeverDuplicatesOutput() throws Exception {
        FixedGameClock clock = new FixedGameClock(NOW);
        PlayerSessionService sessions = sessions(clock);
        sessions.start();
        try {
            sessions.login(PLAYER, "Branz").get();
            FakeCraftingRepository repository = new FakeCraftingRepository();
            FakeEconomy economy = new FakeEconomy(EconomyPaymentPort.Status.PAID);
            DefaultCraftingService service = new DefaultCraftingService(
                    repository, economy, sessions, DefaultCraftingServiceTest::snapshot, clock);
            OperationId operation =
                    OperationId.of("craft", RECIPE.toString(), PLAYER, "batch-42");

            var started = service.begin(PLAYER, RECIPE, Set.of("branz:forge"),
                    Set.of(), operation);
            assertEquals(CraftJob.Status.IN_PROGRESS, started.job().status());
            assertFalse(repository.inventory.materials().containsKey(ORE));
            clock.advance(Duration.ofSeconds(2));
            repository.failFinalizeOnce = true;

            assertThrows(RuntimeException.class, () -> service.complete(operation));
            assertEquals(CraftJob.Status.IN_PROGRESS, repository.job.status());
            assertFalse(repository.inventory.materials().containsKey(INGOT));

            var completed = service.complete(operation);
            var replay = service.complete(operation);
            assertTrue(completed.outputDelivered());
            assertEquals(1L, repository.inventory.materials().get(INGOT));
            assertEquals(10L, repository.profession.totalXp());
            assertFalse(replay.outputDelivered());
            assertEquals(1L, repository.inventory.materials().get(INGOT));
            assertEquals(1, economy.calls);
        } finally {
            sessions.stop();
        }
    }

    @Test
    void unavailableWalletKeepsEscrowAndInsufficientFundsRefundsIt() throws Exception {
        FixedGameClock clock = new FixedGameClock(NOW);
        PlayerSessionService sessions = sessions(clock);
        sessions.start();
        try {
            sessions.login(PLAYER, "Branz").get();
            FakeCraftingRepository repository = new FakeCraftingRepository();
            FakeEconomy economy = new FakeEconomy(EconomyPaymentPort.Status.UNAVAILABLE);
            DefaultCraftingService service = new DefaultCraftingService(
                    repository, economy, sessions, DefaultCraftingServiceTest::snapshot, clock);
            OperationId operation =
                    OperationId.of("craft", RECIPE.toString(), PLAYER, "batch-43");

            var pending = service.begin(PLAYER, RECIPE, Set.of("branz:forge"),
                    Set.of(), operation);
            assertEquals(CraftJob.Status.PENDING_PAYMENT, pending.job().status());
            assertFalse(repository.inventory.materials().containsKey(ORE));

            economy.status = EconomyPaymentPort.Status.INSUFFICIENT;
            var cancelled = service.resumePayment(operation);
            assertEquals(CraftJob.Status.CANCELLED, cancelled.job().status());
            assertEquals(3L, repository.inventory.materials().get(ORE));
            assertFalse(repository.inventory.materials().containsKey(INGOT));
        } finally {
            sessions.stop();
        }
    }

    private static PlayerSessionService sessions(FixedGameClock clock) {
        return new PlayerSessionService(new FakePlayerProfileRepository(),
                new DirectScheduler(), clock, () -> 1L, DuplicateLoginPolicy.CLOSE_PREVIOUS);
    }

    private static ContentSnapshot snapshot() {
        MaterialDefinition ore = new MaterialDefinition(
                ORE, "Ore", "ore", "common", true, 99);
        MaterialDefinition ingot = new MaterialDefinition(
                INGOT, "Ingot", "ingot", "uncommon", true, 99);
        ProfessionDefinition profession = new ProfessionDefinition(
                PROFESSION, "Blacksmithing", 100, 100, 1.6);
        RecipeDefinition recipe = new RecipeDefinition(
                RECIPE, "Refine", Map.of(ORE, 3L), Map.of(), 5,
                "branz:forge", Optional.of(PROFESSION), 1, 2000,
                new RecipeDefinition.Output(INGOT, 1,
                        RecipeDefinition.Output.Binding.UNBOUND, "fixed"),
                10, 10);
        Map<ContentId, ContentDefinition> all =
                Map.of(ORE, ore, INGOT, ingot, PROFESSION, profession, RECIPE, recipe);
        return new ContentSnapshot() {
            @Override public long revision() { return 1; }
            @Override public Instant loadedAt() { return NOW; }
            @Override public Collection<ContentDefinition> definitions() {
                return all.values();
            }
            @Override public Optional<ContentDefinition> find(ContentId id) {
                return Optional.ofNullable(all.get(id));
            }
            @Override public <T extends ContentDefinition> Optional<T> find(
                    ContentId id, Class<T> type) {
                return find(id).filter(type::isInstance).map(type::cast);
            }
            @Override public Map<ContentId, MaterialDefinition> materials() {
                return Map.of(ORE, ore, INGOT, ingot);
            }
            @Override public Map<ContentId, SkillDefinition> skills() { return Map.of(); }
            @Override public Map<ContentId, LifeSkillDefinition> lifeSkills() { return Map.of(); }
            @Override public Map<ContentId, LifeSkillNodeDefinition> lifeSkillNodes() {
                return Map.of();
            }
            @Override public Map<ContentId, MasteryDefinition> masteries() { return Map.of(); }
            @Override public Map<ContentId, WeaponDefinition> weapons() { return Map.of(); }
            @Override public Map<ContentId, LootDefinition> lootTables() { return Map.of(); }
            @Override public Map<ContentId, GatheringNodeDefinition> gatheringNodes() {
                return Map.of();
            }
            @Override public Map<ContentId, ProfessionDefinition> professions() {
                return Map.of(PROFESSION, profession);
            }
            @Override public Map<ContentId, RecipeDefinition> recipes() {
                return Map.of(RECIPE, recipe);
            }
        };
    }

    private static final class FakeEconomy implements EconomyPaymentPort {
        private Status status;
        private int calls;
        private FakeEconomy(Status status) { this.status = status; }
        @Override public long coins(UUID playerId) { return 100; }
        @Override public PaymentResult chargeCoins(
                UUID playerId, long amount, String purchaseId, OperationId operationId) {
            calls++;
            return new PaymentResult(status, status.name(), status == Status.PAID ? amount : 0);
        }
    }

    private static final class FakeCraftingRepository implements CraftingRepository {
        private InventorySnapshot inventory = new InventorySnapshot(
                PLAYER, 36, Map.of(ORE, 3L), Map.of(), Map.of(), Map.of(), Map.of(), NOW);
        private ProfessionSnapshot profession = ProfessionSnapshot.untrained(PROFESSION, NOW);
        private CraftJob job;
        private boolean failFinalizeOnce;
        private long sequence = 1;

        @Override public long allocateSequence(UUID player, ContentId recipe) {
            return sequence++;
        }
        @Override public ProfessionSnapshot profession(UUID player, ContentId id) {
            return profession;
        }
        @Override public Optional<CraftJob> job(OperationId operation) {
            return job == null || !job.operationId().equals(operation)
                    ? Optional.empty() : Optional.of(job);
        }
        @Override public Optional<CraftJob> activeJob(UUID player) {
            return job == null || job.status() == CraftJob.Status.COMPLETE
                    || job.status() == CraftJob.Status.CANCELLED
                    ? Optional.empty() : Optional.of(job);
        }
        @Override
        public CraftPrepareCommit prepare(
                UUID player, RecipeDefinition recipe, long revision,
                Map<ContentId, Long> escrow, OperationId operation, Instant now,
                UnaryOperator<InventorySnapshot> consume) {
            if (job != null) return new CraftPrepareCommit(false, job, inventory, inventory);
            InventorySnapshot before = inventory;
            InventorySnapshot after = consume.apply(before);
            job = new CraftJob(operation, player, recipe.id(), revision,
                    CraftJob.Status.PENDING_PAYMENT, escrow, recipe.coinFee(),
                    recipe.durationMillis(), recipe.output().itemId(), recipe.output().quantity(),
                    recipe.output().binding(), recipe.output().qualityPolicy(),
                    recipe.professionId(), recipe.professionXp(), recipe.trivialAfterLevel(),
                    Optional.empty(), Optional.empty(), now, now);
            inventory = after;
            return new CraftPrepareCommit(true, job, before, after);
        }
        @Override public CraftJob markPaymentSettled(
                OperationId operation, Instant readyAt, Instant now) {
            if (job.status() == CraftJob.Status.PENDING_PAYMENT) {
                job = copy(CraftJob.Status.IN_PROGRESS,
                        Optional.of(readyAt), Optional.empty(), now);
            }
            return job;
        }
        @Override public CraftJob cancel(
                OperationId operation, String reason, Instant now,
                UnaryOperator<InventorySnapshot> refund) {
            inventory = refund.apply(inventory);
            job = copy(CraftJob.Status.CANCELLED,
                    Optional.empty(), Optional.of(reason), now);
            return job;
        }
        @Override
        public CraftFinalizeCommit finalizeCraft(
                OperationId operation, Optional<ContentId> professionId, Instant now,
                UnaryOperator<InventorySnapshot> deliver,
                UnaryOperator<ProfessionSnapshot> professionMutation) {
            Optional<ProfessionSnapshot> beforeProfession = Optional.of(profession);
            if (job.status() == CraftJob.Status.COMPLETE) {
                return new CraftFinalizeCommit(false, job, inventory, inventory,
                        beforeProfession, beforeProfession);
            }
            InventorySnapshot before = inventory;
            InventorySnapshot after = deliver.apply(before);
            ProfessionSnapshot afterProfession = professionMutation.apply(profession);
            if (failFinalizeOnce) {
                failFinalizeOnce = false;
                throw new RuntimeException("injected pre-commit failure");
            }
            inventory = after;
            profession = afterProfession;
            job = copy(CraftJob.Status.COMPLETE, job.readyAt(), Optional.empty(), now);
            return new CraftFinalizeCommit(true, job, before, after,
                    beforeProfession, Optional.of(afterProfession));
        }
        private CraftJob copy(
                CraftJob.Status status, Optional<Instant> ready,
                Optional<String> failure, Instant now) {
            return new CraftJob(job.operationId(), job.playerId(), job.recipeId(),
                    job.contentRevision(), status, job.escrowedMaterials(), job.coinFee(),
                    job.durationMillis(), job.outputItemId(), job.outputQuantity(),
                    job.outputBinding(), job.qualityPolicy(), job.professionId(),
                    job.professionXp(), job.trivialAfterLevel(), ready, failure,
                    job.createdAt(), now);
        }
    }
}
