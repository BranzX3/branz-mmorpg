package com.branz.mmorpg.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.item.InventoryMutationCommit;
import com.branz.mmorpg.api.item.InventoryRepository;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.item.LootDefinition;
import com.branz.mmorpg.api.item.LootEntry;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.skill.SkillDefinition;
import com.branz.mmorpg.core.fixture.FixedGameClock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class DefaultLootServiceTest {
    private static final UUID PLAYER =
            UUID.fromString("9a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");
    private static final UUID PLAYER_TWO =
            UUID.fromString("8a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f7");
    private static final ContentId ORE = ContentId.parse("branz:aether_ore");
    private static final ContentId DUST = ContentId.parse("branz:aether_dust");
    private static final ContentId TABLE = ContentId.parse("branz:test_cache");
    private static final Instant NOW = Instant.parse("2026-07-26T00:00:00Z");

    @Test
    void retryAfterPartialDeliveryCompletesWithoutDuplicatingFirstEntry() {
        FaultRepository repository = new FaultRepository();
        DefaultInventoryService inventory = new DefaultInventoryService(
                repository, DefaultLootServiceTest::snapshot,
                FixedGameClock.at("2026-07-26T00:00:00Z"));
        DefaultLootService loot = new DefaultLootService(
                inventory, DefaultLootServiceTest::snapshot,
                FixedGameClock.at("2026-07-26T00:00:00Z"));
        repository.failCall(2);

        assertThrows(RuntimeException.class, () -> loot.resolvePersonal(
                PLAYER, TABLE, "encounter-42-player-1", true, Set.of(), Map.of()));
        assertEquals(2L, repository.state.materials().get(ORE));
        assertFalse(repository.state.materials().containsKey(DUST));

        var retry = loot.resolvePersonal(
                PLAYER, TABLE, "encounter-42-player-1", true, Set.of(), Map.of());
        assertTrue(retry.newlyApplied());
        assertEquals(2L, repository.state.materials().get(ORE));
        assertEquals(3L, repository.state.materials().get(DUST));

        var replay = loot.resolvePersonal(
                PLAYER, TABLE, "encounter-42-player-1", true, Set.of(), Map.of());
        assertFalse(replay.newlyApplied());
        assertEquals(2L, repository.state.materials().get(ORE));
        assertEquals(3L, repository.state.materials().get(DUST));
    }

    @Test
    void partyRollAssignsEachAwardOnceAndReplayIsStable() {
        MultiPlayerRepository repository = new MultiPlayerRepository();
        DefaultInventoryService inventory = new DefaultInventoryService(
                repository, DefaultLootServiceTest::partySnapshot,
                FixedGameClock.at("2026-07-26T00:00:00Z"));
        DefaultLootService loot = new DefaultLootService(
                inventory, DefaultLootServiceTest::partySnapshot,
                FixedGameClock.at("2026-07-26T00:00:00Z"));

        var first = loot.resolveParty(Set.of(PLAYER, PLAYER_TWO), TABLE,
                "mob-44-party-7", Set.of(), Map.of());
        long delivered = repository.load(PLAYER).materials().values().stream()
                .mapToLong(Long::longValue).sum()
                + repository.load(PLAYER_TWO).materials().values().stream()
                .mapToLong(Long::longValue).sum();
        var replay = loot.resolveParty(Set.of(PLAYER, PLAYER_TWO), TABLE,
                "mob-44-party-7", Set.of(), Map.of());
        long afterReplay = repository.load(PLAYER).materials().values().stream()
                .mapToLong(Long::longValue).sum()
                + repository.load(PLAYER_TWO).materials().values().stream()
                .mapToLong(Long::longValue).sum();

        assertEquals(5, delivered);
        assertEquals(delivered, afterReplay);
        assertEquals(first.keySet(), replay.keySet());
        assertTrue(replay.values().stream().noneMatch(
                com.branz.mmorpg.api.item.LootRollResult::newlyApplied));
    }

    private static ContentSnapshot snapshot() {
        MaterialDefinition ore = new MaterialDefinition(
                ORE, "Ore", "ore", "common", true, 99);
        MaterialDefinition dust = new MaterialDefinition(
                DUST, "Dust", "dust", "common", true, 99);
        LootDefinition loot = new LootDefinition(TABLE, "Cache",
                LootDefinition.Ownership.PERSONAL, 0, true, List.of(
                new LootEntry("ore", ORE, 0, true, 2, 2, Set.of(), 0, 2),
                new LootEntry("dust", DUST, 0, true, 3, 3, Set.of(), 0, 3)));
        Map<ContentId, ContentDefinition> definitions =
                Map.of(ORE, ore, DUST, dust, TABLE, loot);
        return new ContentSnapshot() {
            @Override public long revision() { return 1; }
            @Override public Instant loadedAt() { return NOW; }
            @Override public Collection<ContentDefinition> definitions() {
                return definitions.values();
            }
            @Override public Optional<ContentDefinition> find(ContentId id) {
                return Optional.ofNullable(definitions.get(id));
            }
            @Override public <T extends ContentDefinition> Optional<T> find(
                    ContentId id, Class<T> type) {
                return find(id).filter(type::isInstance).map(type::cast);
            }
            @Override public Map<ContentId, MaterialDefinition> materials() {
                return Map.of(ORE, ore, DUST, dust);
            }
            @Override public Map<ContentId, SkillDefinition> skills() { return Map.of(); }
            @Override public Map<ContentId, LifeSkillDefinition> lifeSkills() { return Map.of(); }
            @Override public Map<ContentId, LifeSkillNodeDefinition> lifeSkillNodes() {
                return Map.of();
            }
            @Override public Map<ContentId, MasteryDefinition> masteries() { return Map.of(); }
            @Override public Map<ContentId, WeaponDefinition> weapons() { return Map.of(); }
            @Override public Map<ContentId, LootDefinition> lootTables() {
                return Map.of(TABLE, loot);
            }
        };
    }

    private static ContentSnapshot partySnapshot() {
        ContentSnapshot personal = snapshot();
        LootDefinition party = new LootDefinition(TABLE, "Party Cache",
                LootDefinition.Ownership.PARTY, 0, true, List.of(
                new LootEntry("ore", ORE, 0, true, 2, 2, Set.of(), 0, 2),
                new LootEntry("dust", DUST, 0, true, 3, 3, Set.of(), 0, 3)));
        return new ContentSnapshot() {
            @Override public long revision() { return personal.revision(); }
            @Override public Instant loadedAt() { return personal.loadedAt(); }
            @Override public Collection<ContentDefinition> definitions() {
                return List.of(personal.find(ORE).orElseThrow(),
                        personal.find(DUST).orElseThrow(), party);
            }
            @Override public Optional<ContentDefinition> find(ContentId id) {
                return id.equals(TABLE) ? Optional.of(party) : personal.find(id);
            }
            @Override public <T extends ContentDefinition> Optional<T> find(
                    ContentId id, Class<T> type) {
                return find(id).filter(type::isInstance).map(type::cast);
            }
            @Override public Map<ContentId, MaterialDefinition> materials() {
                return personal.materials();
            }
            @Override public Map<ContentId, SkillDefinition> skills() { return Map.of(); }
            @Override public Map<ContentId, LifeSkillDefinition> lifeSkills() { return Map.of(); }
            @Override public Map<ContentId, LifeSkillNodeDefinition> lifeSkillNodes() {
                return Map.of();
            }
            @Override public Map<ContentId, MasteryDefinition> masteries() { return Map.of(); }
            @Override public Map<ContentId, WeaponDefinition> weapons() { return Map.of(); }
            @Override public Map<ContentId, LootDefinition> lootTables() {
                return Map.of(TABLE, party);
            }
        };
    }

    private static final class FaultRepository implements InventoryRepository {
        private InventorySnapshot state = InventorySnapshot.empty(PLAYER, 36, NOW);
        private final Set<OperationId> operations = new HashSet<>();
        private int calls;
        private int failCall;

        void failCall(int call) { failCall = call; }

        @Override public InventorySnapshot load(UUID playerId) { return state; }

        @Override
        public synchronized InventoryMutationCommit mutate(
                UUID playerId, OperationId operationId, long delivered, long overflowed,
                UnaryOperator<InventorySnapshot> mutation) {
            calls++;
            InventorySnapshot before = state;
            if (operations.contains(operationId)) {
                return new InventoryMutationCommit(false, before, before, 0, 0);
            }
            InventorySnapshot after = mutation.apply(before);
            if (calls == failCall) throw new RuntimeException("injected pre-commit failure");
            operations.add(operationId);
            state = after;
            return new InventoryMutationCommit(true, before, after, delivered, overflowed);
        }
    }

    private static final class MultiPlayerRepository implements InventoryRepository {
        private final Map<UUID, InventorySnapshot> states = new java.util.HashMap<>();
        private final Set<OperationId> operations = new HashSet<>();

        @Override public synchronized InventorySnapshot load(UUID playerId) {
            return states.computeIfAbsent(playerId,
                    id -> InventorySnapshot.empty(id, 36, NOW));
        }

        @Override public synchronized InventoryMutationCommit mutate(
                UUID playerId, OperationId operationId, long delivered, long overflowed,
                UnaryOperator<InventorySnapshot> mutation) {
            InventorySnapshot before = load(playerId);
            if (!operations.add(operationId)) {
                return new InventoryMutationCommit(false, before, before, 0, 0);
            }
            InventorySnapshot after = mutation.apply(before);
            states.put(playerId, after);
            return new InventoryMutationCommit(
                    true, before, after, delivered, overflowed);
        }
    }
}
