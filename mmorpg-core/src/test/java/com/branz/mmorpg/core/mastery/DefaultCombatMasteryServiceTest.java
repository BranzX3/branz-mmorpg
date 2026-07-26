package com.branz.mmorpg.core.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.mastery.CombatMasteryRepository;
import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.mastery.MasteryMutationCommit;
import com.branz.mmorpg.api.mastery.MasterySnapshot;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.skill.SkillDefinition;
import com.branz.mmorpg.core.fixture.FixedGameClock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class DefaultCombatMasteryServiceTest {

    private static final UUID PLAYER =
            UUID.fromString("9a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");
    private static final ContentId MASTERY = ContentId.parse("branz:broadsword");

    @Test
    void contributionIsIdempotentAndAppliesAntiFarmMultiplier() {
        MasteryDefinition definition = new MasteryDefinition(
                MASTERY, "Broadsword", MasteryDefinition.Kind.WEAPON_TYPE,
                ContentId.parse("branz:sword"), 100, 100, 1.65, 0.20);
        MemoryRepository repository = new MemoryRepository();
        DefaultCombatMasteryService service = new DefaultCombatMasteryService(
                repository, () -> snapshot(definition),
                FixedGameClock.at("2026-07-26T00:00:00Z"));
        OperationId operation = OperationId.of(
                "combat_mastery", MASTERY.toString(), PLAYER, "encounter-42");

        MasteryMutationCommit first =
                service.grantContribution(PLAYER, MASTERY, 100, 0.25, operation);
        MasteryMutationCommit replay =
                service.grantContribution(PLAYER, MASTERY, 100, 0.25, operation);

        assertTrue(first.applied());
        assertEquals(25L, first.awardedXp());
        assertEquals(25L, first.after().totalXp());
        assertFalse(replay.applied());
        assertEquals(0L, replay.awardedXp());
        assertEquals(25L, service.profile(PLAYER).get(MASTERY).totalXp());
    }

    private static ContentSnapshot snapshot(MasteryDefinition definition) {
        return new ContentSnapshot() {
            @Override public long revision() { return 1; }
            @Override public Instant loadedAt() { return Instant.EPOCH; }
            @Override public Collection<ContentDefinition> definitions() {
                return java.util.List.of(definition);
            }
            @Override public Optional<ContentDefinition> find(ContentId id) {
                return id.equals(definition.id()) ? Optional.of(definition) : Optional.empty();
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
            @Override public Map<ContentId, MasteryDefinition> masteries() {
                return Map.of(definition.id(), definition);
            }
            @Override public Map<ContentId, WeaponDefinition> weapons() { return Map.of(); }
        };
    }

    private static final class MemoryRepository implements CombatMasteryRepository {
        private final Map<UUID, Map<ContentId, MasterySnapshot>> profiles = new HashMap<>();
        private final Set<OperationId> operations = new HashSet<>();

        @Override
        public Map<ContentId, MasterySnapshot> load(UUID playerId) {
            return Map.copyOf(profiles.getOrDefault(playerId, Map.of()));
        }

        @Override
        public synchronized MasteryMutationCommit mutate(
                UUID playerId, ContentId masteryId, OperationId operationId,
                long awardedXp, UnaryOperator<MasterySnapshot> mutation) {
            Map<ContentId, MasterySnapshot> profile =
                    profiles.computeIfAbsent(playerId, ignored -> new HashMap<>());
            MasterySnapshot before = profile.getOrDefault(masteryId,
                    MasterySnapshot.untrained(masteryId, Instant.EPOCH));
            if (!operations.add(operationId)) {
                return new MasteryMutationCommit(false, before, before, 0);
            }
            MasterySnapshot after = mutation.apply(before);
            profile.put(masteryId, after);
            return new MasteryMutationCommit(true, before, after, awardedXp);
        }
    }
}
