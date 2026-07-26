package com.branz.mmorpg.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.crafting.CraftJob;
import com.branz.mmorpg.api.crafting.ProfessionSnapshot;
import com.branz.mmorpg.api.crafting.RecipeDefinition;
import com.branz.mmorpg.api.gathering.GatheringNodeInstance;
import com.branz.mmorpg.api.gathering.GatheringNodeState;
import com.branz.mmorpg.api.gathering.WorldBlockPosition;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.lifeskill.LifeSkillProgress;
import com.branz.mmorpg.api.lifeskill.LifeSkillSnapshot;
import com.branz.mmorpg.api.operation.OperationId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "BRANZ_MYSQL_INTEGRATION", matches = "true")
class JdbcGatheringCraftingIntegrationTest {
    private static final ContentId MINING = ContentId.parse("branz:mining");
    private static final ContentId VEIN = ContentId.parse("branz:iron_vein");
    private static final ContentId ORE = ContentId.parse("branz:iron_ore");
    private static final ContentId INGOT = ContentId.parse("branz:aether_ingot");
    private static final ContentId RECIPE = ContentId.parse("branz:aether_ingot_recipe");
    private static final ContentId PROFESSION = ContentId.parse("branz:blacksmithing");
    private static final Instant NOW = Instant.parse("2026-07-27T08:00:00Z");

    private DatabaseManager database;
    private UUID player;
    private UUID other;

    @BeforeEach
    void connect() {
        database = connectDatabase();
        player = UUID.randomUUID();
        other = UUID.randomUUID();
        JdbcPlayerProfileRepository profiles = new JdbcPlayerProfileRepository(database);
        profiles.loadOrCreate(player, "I8Gatherer");
        profiles.loadOrCreate(other, "I8Contender");
    }

    @AfterEach
    void close() {
        if (database != null) database.close();
    }

    @Test
    void contestedHarvestInterruptReplayAndRespawnSurviveRestart() throws Exception {
        JdbcGatheringNodeRepository nodes = new JdbcGatheringNodeRepository(database);
        GatheringNodeInstance placed = GatheringNodeInstance.placed(
                UUID.randomUUID(), VEIN,
                new WorldBlockPosition(UUID.randomUUID(), 12, 64, -7), player, NOW);
        nodes.place(placed);

        CyclicBarrier start = new CyclicBarrier(2);
        var outcomes = new ArrayList<Object>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> reserve(nodes, placed, player, start));
            var second = executor.submit(() -> reserve(nodes, placed, other, start));
            outcomes.add(first.get());
            outcomes.add(second.get());
        }
        var winner = outcomes.stream().filter(GatheringNodeInstance.class::isInstance)
                .map(GatheringNodeInstance.class::cast).findFirst().orElseThrow();
        assertEquals(1, outcomes.stream().filter(GatheringNodeInstance.class::isInstance).count());
        assertEquals(1, outcomes.stream().filter(RuntimeException.class::isInstance).count());

        GatheringNodeInstance released = nodes.release(
                winner.instanceId(), winner.reservedBy().orElseThrow(),
                winner.reservationSequence(), NOW.plusSeconds(1));
        assertEquals(GatheringNodeState.AVAILABLE, released.state());
        assertEquals(0, new JdbcPlayerProfileRepository(database)
                .loadLifeSkills(winner.reservedBy().orElseThrow()).skill(MINING).totalXp());

        UUID harvester = winner.reservedBy().orElseThrow();
        GatheringNodeInstance reserved = nodes.reserve(
                placed.instanceId(), harvester, NOW.plusSeconds(2),
                Duration.ofSeconds(3), Duration.ofSeconds(2));
        Instant completedAt = NOW.plusSeconds(6);
        Instant respawnAt = completedAt.plusSeconds(180);
        OperationId operation = OperationId.of(
                "gathering", VEIN.value(), harvester,
                placed.instanceId().toString().replace("-", "") + "-" + reserved.reservationSequence());

        var committed = nodes.commitHarvest(
                placed.instanceId(), harvester, reserved.reservationSequence(), MINING,
                operation, completedAt, respawnAt,
                before -> skillWithXp(before, 6, completedAt),
                before -> addMaterial(before, ORE, 2, completedAt));
        var replay = nodes.commitHarvest(
                placed.instanceId(), harvester, reserved.reservationSequence(), MINING,
                operation, completedAt, respawnAt,
                before -> { throw new AssertionError("duplicate XP mutation executed"); },
                before -> { throw new AssertionError("duplicate yield mutation executed"); });

        assertTrue(committed.applied());
        assertFalse(replay.applied());
        assertEquals(6, new JdbcPlayerProfileRepository(database)
                .loadLifeSkills(harvester).skill(MINING).totalXp());
        assertEquals(2, new JdbcInventoryRepository(database).load(harvester)
                .materials().get(ORE));
        assertEquals(GatheringNodeState.DEPLETED, replay.nodeAfter().state());

        database.close();
        database = connectDatabase();
        JdbcGatheringNodeRepository restartedNodes =
                new JdbcGatheringNodeRepository(database);
        GatheringNodeInstance afterRestart =
                restartedNodes.find(placed.instanceId()).orElseThrow();
        assertEquals(respawnAt, afterRestart.respawnAt().orElseThrow());
        GatheringNodeInstance nextReservation = restartedNodes.reserve(
                placed.instanceId(), harvester, respawnAt.plusMillis(1),
                Duration.ofSeconds(1), Duration.ofSeconds(2));
        assertEquals(GatheringNodeState.RESERVED, nextReservation.state());
        assertTrue(nextReservation.reservationSequence() > reserved.reservationSequence());
    }

    @Test
    void craftEscrowPaymentStateAndFinalizationAreExactlyOnceAcrossRepositoryRestart() {
        JdbcInventoryRepository inventory = new JdbcInventoryRepository(database);
        inventory.mutate(player, OperationId.of("inventory", ORE.value(), player, "i8-seed"),
                6, 0, before -> addMaterial(before, ORE, 6, NOW));
        JdbcCraftingRepository crafting = new JdbcCraftingRepository(database);
        RecipeDefinition recipe = recipe();
        OperationId operation = OperationId.of("craft", RECIPE.value(), player, "i8-batch-1");

        assertEquals(1, crafting.allocateSequence(player, RECIPE));
        assertEquals(2, crafting.allocateSequence(player, RECIPE));
        var prepared = crafting.prepare(player, recipe, 8, recipe.inputs(), operation, NOW,
                before -> removeMaterial(before, ORE, 3, NOW));
        var prepareReplay = crafting.prepare(player, recipe, 8, recipe.inputs(), operation, NOW,
                before -> { throw new AssertionError("duplicate escrow mutation executed"); });
        assertTrue(prepared.created());
        assertFalse(prepareReplay.created());
        assertEquals(3, inventory.load(player).materials().get(ORE));
        assertEquals(CraftJob.Status.PENDING_PAYMENT,
                crafting.activeJob(player).orElseThrow().status());

        Instant readyAt = NOW.plusSeconds(2);
        assertEquals(CraftJob.Status.IN_PROGRESS,
                crafting.markPaymentSettled(operation, readyAt, NOW).status());
        crafting = new JdbcCraftingRepository(database);
        assertEquals(CraftJob.Status.IN_PROGRESS,
                crafting.activeJob(player).orElseThrow().status());

        var finalized = crafting.finalizeCraft(operation, Optional.of(PROFESSION), readyAt,
                before -> addMaterial(before, INGOT, 1, readyAt),
                before -> new ProfessionSnapshot(PROFESSION, 1, 10, readyAt));
        var replay = crafting.finalizeCraft(operation, Optional.of(PROFESSION), readyAt,
                before -> { throw new AssertionError("duplicate output mutation executed"); },
                before -> { throw new AssertionError("duplicate profession mutation executed"); });

        assertTrue(finalized.applied());
        assertFalse(replay.applied());
        assertEquals(1, inventory.load(player).materials().get(INGOT));
        assertEquals(10, crafting.profession(player, PROFESSION).totalXp());
        assertTrue(crafting.activeJob(player).isEmpty());
        assertEquals(CraftJob.Status.COMPLETE, crafting.job(operation).orElseThrow().status());
    }

    private static Object reserve(JdbcGatheringNodeRepository nodes,
                                  GatheringNodeInstance placed, UUID contender,
                                  CyclicBarrier start) {
        try {
            start.await();
            return nodes.reserve(placed.instanceId(), contender, NOW,
                    Duration.ofSeconds(3), Duration.ofSeconds(2));
        } catch (RuntimeException failure) {
            return failure;
        } catch (Exception failure) {
            return new RuntimeException(failure);
        }
    }

    private static LifeSkillSnapshot skillWithXp(
            LifeSkillSnapshot before, long xp, Instant now) {
        return new LifeSkillSnapshot(new LifeSkillProgress(
                before.skillId(), before.level(), before.totalXp() + xp,
                before.unspentPoints(), before.progress().treeRevision(), now),
                before.nodeRanks());
    }

    private static InventorySnapshot addMaterial(
            InventorySnapshot before, ContentId id, long quantity, Instant now) {
        Map<ContentId, Long> materials = new java.util.LinkedHashMap<>(before.materials());
        materials.merge(id, quantity, Math::addExact);
        return inventory(before, materials, now);
    }

    private static InventorySnapshot removeMaterial(
            InventorySnapshot before, ContentId id, long quantity, Instant now) {
        Map<ContentId, Long> materials = new java.util.LinkedHashMap<>(before.materials());
        long remaining = materials.getOrDefault(id, 0L) - quantity;
        if (remaining < 0) throw new IllegalStateException("not enough material");
        if (remaining == 0) materials.remove(id);
        else materials.put(id, remaining);
        return inventory(before, materials, now);
    }

    private static InventorySnapshot inventory(
            InventorySnapshot before, Map<ContentId, Long> materials, Instant now) {
        return new InventorySnapshot(before.playerId(), before.slotCapacity(), materials,
                before.items(), before.equipped(), before.pendingMaterials(),
                before.pendingItems(), now);
    }

    private static RecipeDefinition recipe() {
        return new RecipeDefinition(RECIPE, "Refine Aether Ingot", Map.of(ORE, 3L), Map.of(),
                5, "branz:forge", Optional.of(PROFESSION), 1, 2000,
                new RecipeDefinition.Output(INGOT, 1,
                        RecipeDefinition.Output.Binding.UNBOUND, "fixed"), 10, 10);
    }

    private static DatabaseManager connectDatabase() {
        return DatabaseManager.connect(new DatabaseConfig(
                environment("BRANZ_MYSQL_HOST", "127.0.0.1"),
                Integer.parseInt(environment("BRANZ_MYSQL_PORT", "3407")),
                environment("BRANZ_MYSQL_DATABASE", "branz_mmorpg_test"),
                environment("BRANZ_MYSQL_USERNAME", "root"),
                environment("BRANZ_MYSQL_PASSWORD", ""), 4, 5000));
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
