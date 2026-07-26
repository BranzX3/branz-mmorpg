package com.branz.mmorpg.core.gathering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.gathering.GatheringHarvestCommit;
import com.branz.mmorpg.api.gathering.GatheringNodeDefinition;
import com.branz.mmorpg.api.gathering.GatheringNodeInstance;
import com.branz.mmorpg.api.gathering.GatheringNodeRepository;
import com.branz.mmorpg.api.gathering.GatheringNodeState;
import com.branz.mmorpg.api.gathering.GatheringYieldDefinition;
import com.branz.mmorpg.api.gathering.WorldBlockPosition;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.item.LootDefinition;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillSnapshot;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class DefaultGatheringServiceTest {
    private static final UUID PLAYER =
            UUID.fromString("9a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");
    private static final UUID WORLD =
            UUID.fromString("5a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f4");
    private static final ContentId MINING = ContentId.parse("branz:mining");
    private static final ContentId DEPOSIT = ContentId.parse("branz:aether_deposit");
    private static final ContentId ORE = ContentId.parse("branz:aether_ore");
    private static final Instant NOW = Instant.parse("2026-07-26T00:00:00Z");

    @Test
    void registeredNodeCommitsXpYieldAndDepletionExactlyOnce() throws Exception {
        FixedGameClock clock = new FixedGameClock(NOW);
        PlayerSessionService sessions = sessions(clock);
        sessions.start();
        try {
            sessions.login(PLAYER, "Branz").get();
            FakeNodes nodes = new FakeNodes();
            DefaultGatheringService service = new DefaultGatheringService(
                    nodes, sessions, DefaultGatheringServiceTest::snapshot, clock);
            WorldBlockPosition position = new WorldBlockPosition(WORLD, 1, 64, 2);
            service.place(DEPOSIT, position, PLAYER);

            var reservation = service.begin(
                    PLAYER, position, Set.of("branz:pickaxe"), true, true);
            clock.advance(Duration.ofMillis(2500));
            var first = service.complete(reservation);
            var replay = service.complete(reservation);

            assertTrue(first.applied());
            assertEquals(3, first.awardedXp());
            assertEquals(3, sessions.profile(PLAYER).skill(MINING).totalXp());
            assertTrue(nodes.inventory.materials().get(ORE) >= 1);
            long quantity = nodes.inventory.materials().get(ORE);
            assertFalse(replay.applied());
            assertEquals(quantity, nodes.inventory.materials().get(ORE));
            assertEquals(GatheringNodeState.DEPLETED, first.node().state());
        } finally {
            sessions.stop();
        }
    }

    @Test
    void ordinaryBlockAndWrongToolGrantNothing() throws Exception {
        FixedGameClock clock = new FixedGameClock(NOW);
        PlayerSessionService sessions = sessions(clock);
        sessions.start();
        try {
            sessions.login(PLAYER, "Branz").get();
            DefaultGatheringService service = new DefaultGatheringService(
                    new FakeNodes(), sessions, DefaultGatheringServiceTest::snapshot, clock);
            WorldBlockPosition position = new WorldBlockPosition(WORLD, 1, 64, 2);
            assertThrows(IllegalArgumentException.class, () -> service.begin(
                    PLAYER, position, Set.of("branz:pickaxe"), true, true));
            service.place(DEPOSIT, position, PLAYER);
            assertThrows(IllegalStateException.class, () -> service.begin(
                    PLAYER, position, Set.of("branz:axe"), true, true));
            assertEquals(0, sessions.profile(PLAYER).skill(MINING).totalXp());
        } finally {
            sessions.stop();
        }
    }

    private static PlayerSessionService sessions(FixedGameClock clock) {
        return new PlayerSessionService(new FakePlayerProfileRepository(),
                new DirectScheduler(), clock, () -> 1L, DuplicateLoginPolicy.CLOSE_PREVIOUS);
    }

    private static ContentSnapshot snapshot() {
        LifeSkillDefinition mining = new LifeSkillDefinition(
                MINING, "Mining", 100, 75, 1.55, Set.of(2, 5, 10));
        MaterialDefinition ore = new MaterialDefinition(
                ORE, "Aether Ore", "ore", "uncommon", true, 99);
        GatheringNodeDefinition node = new GatheringNodeDefinition(
                DEPOSIT, "Aether Deposit", MINING, GatheringNodeDefinition.Tier.UNCOMMON,
                3, "branz:pickaxe", 1, 2500, 60_000, 0,
                Set.of("branz:common_mining"),
                new GatheringNodeDefinition.Presentation(
                        "minecraft:amethyst_block", "minecraft:stone", "Aether"),
                List.of(new GatheringYieldDefinition(ORE, 1, 3, 1)));
        Map<ContentId, ContentDefinition> all =
                Map.of(MINING, mining, ORE, ore, DEPOSIT, node);
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
                return Map.of(ORE, ore);
            }
            @Override public Map<ContentId, SkillDefinition> skills() { return Map.of(); }
            @Override public Map<ContentId, LifeSkillDefinition> lifeSkills() {
                return Map.of(MINING, mining);
            }
            @Override public Map<ContentId, LifeSkillNodeDefinition> lifeSkillNodes() {
                return Map.of();
            }
            @Override public Map<ContentId, MasteryDefinition> masteries() { return Map.of(); }
            @Override public Map<ContentId, WeaponDefinition> weapons() { return Map.of(); }
            @Override public Map<ContentId, LootDefinition> lootTables() { return Map.of(); }
            @Override public Map<ContentId, GatheringNodeDefinition> gatheringNodes() {
                return Map.of(DEPOSIT, node);
            }
        };
    }

    private static final class FakeNodes implements GatheringNodeRepository {
        private final Map<UUID, GatheringNodeInstance> nodes = new HashMap<>();
        private final Set<OperationId> operations = new HashSet<>();
        private final GatheringNodeEngine engine = new GatheringNodeEngine();
        private LifeSkillSnapshot skill = LifeSkillSnapshot.untrained(MINING, NOW);
        private InventorySnapshot inventory = InventorySnapshot.empty(PLAYER, 36, NOW);

        @Override public GatheringNodeInstance place(GatheringNodeInstance node) {
            nodes.put(node.instanceId(), node);
            return node;
        }
        @Override public Optional<GatheringNodeInstance> findAt(WorldBlockPosition position) {
            return nodes.values().stream().filter(node -> node.position().equals(position)).findFirst();
        }
        @Override public Optional<GatheringNodeInstance> find(UUID id) {
            return Optional.ofNullable(nodes.get(id));
        }
        @Override public Collection<GatheringNodeInstance> list() {
            return List.copyOf(nodes.values());
        }
        @Override public boolean remove(UUID id) { return nodes.remove(id) != null; }
        @Override public GatheringNodeInstance reserve(
                UUID id, UUID player, Instant now, Duration harvest, Duration grace) {
            GatheringNodeInstance reserved =
                    engine.reserve(nodes.get(id), player, now, harvest, grace);
            nodes.put(id, reserved);
            return reserved;
        }
        @Override public GatheringNodeInstance release(
                UUID id, UUID player, long sequence, Instant now) {
            GatheringNodeInstance released =
                    engine.release(nodes.get(id), player, sequence, now);
            nodes.put(id, released);
            return released;
        }
        @Override
        public GatheringHarvestCommit commitHarvest(
                UUID id, UUID player, long sequence, ContentId skillId,
                OperationId operation, Instant now, Instant respawnAt,
                UnaryOperator<LifeSkillSnapshot> skillMutation,
                UnaryOperator<InventorySnapshot> inventoryMutation) {
            GatheringNodeInstance beforeNode = nodes.get(id);
            if (!operations.add(operation)) {
                return new GatheringHarvestCommit(false, beforeNode, beforeNode,
                        skill, skill, inventory, inventory);
            }
            LifeSkillSnapshot beforeSkill = skill;
            InventorySnapshot beforeInventory = inventory;
            GatheringNodeInstance afterNode =
                    engine.deplete(beforeNode, player, sequence, now, respawnAt);
            LifeSkillSnapshot afterSkill = skillMutation.apply(beforeSkill);
            InventorySnapshot afterInventory = inventoryMutation.apply(beforeInventory);
            nodes.put(id, afterNode);
            skill = afterSkill;
            inventory = afterInventory;
            return new GatheringHarvestCommit(true, beforeNode, afterNode,
                    beforeSkill, afterSkill, beforeInventory, afterInventory);
        }
        @Override public GatheringNodeInstance setState(
                UUID id, GatheringNodeState state, Instant now) {
            GatheringNodeInstance changed = state == GatheringNodeState.BROKEN
                    ? engine.broken(nodes.get(id))
                    : engine.normalize(nodes.get(id), Instant.MAX);
            nodes.put(id, changed);
            return changed;
        }
    }
}
