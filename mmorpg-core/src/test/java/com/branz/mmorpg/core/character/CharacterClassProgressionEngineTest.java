package com.branz.mmorpg.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.character.CharacterClassId;
import com.branz.mmorpg.api.character.CharacterClassProgress;
import com.branz.mmorpg.api.character.CharacterClassRole;
import com.branz.mmorpg.api.character.ClassSkillNodeDefinition;
import com.branz.mmorpg.api.character.ClassSkillNodeType;
import com.branz.mmorpg.api.character.StarterGrantPlan;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.skill.ResourceType;
import com.branz.mmorpg.api.stat.AttributeModifier;
import com.branz.mmorpg.api.stat.AttributeType;
import com.branz.mmorpg.api.stat.ModifierSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CharacterClassProgressionEngineTest {
    private static final UUID PLAYER = UUID.fromString("bd18be53-ffbb-470e-aae4-c193715345da");
    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");
    private static final ContentId ROOT = ContentId.parse("branz:warrior_root");
    private static final ContentId ACTIVE = ContentId.parse("branz:rally_node");
    private static final ContentId ULTIMATE = ContentId.parse("branz:ultimate_node");
    private final CharacterClassProgressionEngine engine = engine();

    @Test
    void oneXpGrantCrossesEveryMilestoneAndAwardsPointsExactlyOnce() {
        CharacterClassProgress initial = CharacterClassProgress.initial(
                PLAYER, CharacterClassId.WARRIOR.value(), 1, NOW);
        long throughLevelFive = engine.requiredXp(1) + engine.requiredXp(2)
                + engine.requiredXp(3) + engine.requiredXp(4);

        CharacterClassProgress result = engine.grantXp(initial, throughLevelFive, NOW.plusSeconds(1));

        assertEquals(5, result.level());
        assertEquals(5, result.unspentSkillPoints(),
                "levels 2-5 grant four points and configured level 5 grants one bonus");
        CharacterClassProgress unchanged = engine.grantXp(result, 0, NOW.plusSeconds(2));
        assertEquals(5, unchanged.unspentSkillPoints());
    }

    @Test
    void purchaseValidatesLevelPrerequisitePointsAndMaximumRank() {
        CharacterClassProgress progress = leveled(5);
        CharacterClassProgress beforeRoot = progress;
        assertThrows(IllegalStateException.class,
                () -> engine.purchase(beforeRoot, ACTIVE, NOW), "root prerequisite is missing");
        progress = engine.purchase(progress, ROOT, NOW);
        assertEquals(1, progress.rank(ROOT));
        progress = engine.purchase(progress, ACTIVE, NOW);
        assertEquals(1, progress.rank(ACTIVE));
        CharacterClassProgress finalProgress = progress;
        assertThrows(IllegalStateException.class,
                () -> engine.purchase(finalProgress, ACTIVE, NOW), "rank is capped");
        assertTrue(engine.unlockedSkills(progress).contains(ContentId.parse("branz:rally")));
    }

    @Test
    void fullRespecRefundsPurchasedRanksWithoutChangingPermanentClass() {
        CharacterClassProgress progress = leveled(5);
        progress = engine.purchase(progress, ROOT, NOW);
        progress = engine.purchase(progress, ACTIVE, NOW);
        int beforeRefund = progress.unspentSkillPoints();

        CharacterClassProgress reset = engine.respec(progress, NOW.plusSeconds(1));

        assertEquals(CharacterClassId.WARRIOR.value(), reset.classId());
        assertTrue(reset.nodeRanks().isEmpty());
        assertEquals(beforeRefund + 2, reset.unspentSkillPoints());
    }

    private CharacterClassProgress leveled(int target) {
        long xp = 0;
        for (int level = 1; level < target; level++) xp += engine.requiredXp(level);
        return engine.grantXp(CharacterClassProgress.initial(
                PLAYER, CharacterClassId.WARRIOR.value(), 1, NOW), xp, NOW);
    }

    private static CharacterClassProgressionEngine engine() {
        CharacterClassDefinition definition = new CharacterClassDefinition(
                CharacterClassId.WARRIOR.value(), "Warrior", 1,
                Set.of(CharacterClassRole.DAMAGE), Map.of("max_health", 100.0,
                        "max_stamina", 100.0), ResourceType.STAMINA, Set.of(),
                Set.of("sword"), Set.of("heavy"), List.of(ContentId.parse("branz:rally")),
                ContentId.parse("branz:ultimate"), ROOT,
                new StarterGrantPlan(ContentId.parse("branz:starter"), 1,
                        ContentId.parse("branz:sword"), List.of(ContentId.parse("branz:basic")),
                        Map.of()), Set.of(), 10, 100, 1.0, Set.of(5), 1);
        ModifierSource source = ModifierSource.of(ModifierSource.SourceType.CLASS_TREE, ROOT.toString());
        ClassSkillNodeDefinition root = new ClassSkillNodeDefinition(ROOT, definition.id(), 1,
                "vanguard", ClassSkillNodeType.PASSIVE, 1, 1, 2, Map.of(), Optional.empty(),
                Optional.empty(), List.of(AttributeModifier.flat("def", AttributeType.DEFENSE, 5, source)));
        ClassSkillNodeDefinition active = new ClassSkillNodeDefinition(ACTIVE, definition.id(), 1,
                "warlord", ClassSkillNodeType.ACTIVE_UNLOCK, 1, 1, 3, Map.of(ROOT, 1),
                Optional.empty(), Optional.of(ContentId.parse("branz:rally")), List.of());
        ClassSkillNodeDefinition ultimate = new ClassSkillNodeDefinition(ULTIMATE, definition.id(), 1,
                "warlord", ClassSkillNodeType.ULTIMATE_UNLOCK, 1, 1, 5, Map.of(ACTIVE, 1),
                Optional.empty(), Optional.of(ContentId.parse("branz:ultimate")), List.of());
        return new CharacterClassProgressionEngine(definition,
                Map.of(ROOT, root, ACTIVE, active, ULTIMATE, ultimate));
    }
}
