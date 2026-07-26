package com.branz.mmorpg.core.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.encounter.ContributionType;
import com.branz.mmorpg.api.encounter.EncounterDefinition;
import com.branz.mmorpg.api.encounter.EncounterRepository;
import com.branz.mmorpg.api.encounter.EncounterSnapshot;
import com.branz.mmorpg.api.item.LootRollResult;
import com.branz.mmorpg.api.item.LootService;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.skill.SkillDefinition;
import com.branz.mmorpg.core.fixture.FixedGameClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultEncounterServiceTest {
    private static final UUID PLAYER =
            UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final ContentId ID = ContentId.parse("branz:test_encounter");

    @Test
    void rewardFailureRetriesSameCompletionWithoutDuplicateDelivery() {
        FixedGameClock clock = FixedGameClock.at("2026-07-26T00:00:00Z");
        FakeRepository repository = new FakeRepository();
        CrashAfterDeliveryLoot loot = new CrashAfterDeliveryLoot();
        DefaultEncounterService service = new DefaultEncounterService(
                repository, loot, DefaultEncounterServiceTest::snapshot, clock);
        EncounterSnapshot created = service.create(ID, Set.of(PLAYER));
        clock.advance(Duration.ofMillis(1));
        service.activate(created.instanceId(), Set.of(UUID.randomUUID()), Set.of("w:0:0"));
        service.contribute(created.instanceId(), PLAYER, ContributionType.DAMAGE, 100);
        service.bossHealth(created.instanceId(), 0);

        assertThrows(IllegalStateException.class,
                () -> service.deliverRewards(created.instanceId()));
        EncounterSnapshot retry = service.deliverRewards(created.instanceId());

        assertEquals(1, loot.uniqueDeliveries);
        assertEquals(Set.of(PLAYER), retry.rewardedPlayers());
    }

    private static ContentSnapshot snapshot() {
        EncounterDefinition definition = new EncounterDefinition(
                ID, "Test", EncounterDefinition.Mode.PRIVATE_PARTY,
                ContentId.parse("branz:mob"),
                List.of(new EncounterDefinition.Phase("only", 0,
                        Set.of(ContentId.parse("branz:skill")), Set.of(), 1)),
                20, 0, 1000, 10000, 1, 5, 10,
                EncounterDefinition.PartyPolicy.SNAPSHOT_AT_START,
                false, ContentId.parse("branz:loot"));
        Map<ContentId, ContentDefinition> definitions = Map.of(ID, definition);
        return new ContentSnapshot() {
            @Override public long revision() { return 1; }
            @Override public Instant loadedAt() { return Instant.EPOCH; }
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
            @Override public Map<ContentId, MaterialDefinition> materials() { return Map.of(); }
            @Override public Map<ContentId, SkillDefinition> skills() { return Map.of(); }
            @Override public Map<ContentId, LifeSkillDefinition> lifeSkills() { return Map.of(); }
            @Override public Map<ContentId, LifeSkillNodeDefinition> lifeSkillNodes() {
                return Map.of();
            }
            @Override public Map<ContentId, MasteryDefinition> masteries() { return Map.of(); }
            @Override public Map<ContentId, WeaponDefinition> weapons() { return Map.of(); }
            @Override public Map<ContentId, EncounterDefinition> encounters() {
                return Map.of(ID, definition);
            }
        };
    }

    private static final class FakeRepository implements EncounterRepository {
        private final Map<UUID, EncounterSnapshot> values = new HashMap<>();
        @Override public EncounterSnapshot insert(EncounterSnapshot encounter) {
            values.put(encounter.instanceId(), encounter);
            return encounter;
        }
        @Override public Optional<EncounterSnapshot> find(UUID instanceId) {
            return Optional.ofNullable(values.get(instanceId));
        }
        @Override public Collection<EncounterSnapshot> recoverable() {
            return List.copyOf(values.values());
        }
        @Override public EncounterSnapshot save(EncounterSnapshot encounter) {
            values.put(encounter.instanceId(), encounter);
            return encounter;
        }
    }

    private static final class CrashAfterDeliveryLoot implements LootService {
        private final Set<String> delivered = new java.util.HashSet<>();
        private int calls;
        private int uniqueDeliveries;
        @Override public LootRollResult resolvePersonal(
                UUID playerId, ContentId lootTableId, String durableRollId,
                boolean contributionEligible, Set<String> conditions,
                Map<String, Integer> pityMisses) {
            calls++;
            if (delivered.add(durableRollId)) uniqueDeliveries++;
            if (calls == 1) throw new IllegalStateException("injected crash after delivery");
            return new LootRollResult(durableRollId, true, false, List.of(), 0, 0);
        }
    }
}
