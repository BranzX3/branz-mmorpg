package com.branz.mmorpg.core.lifeskill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillSnapshot;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LifeSkillProgressionEngineTest {

    private static final ContentId MINING = ContentId.parse("branz:mining");
    private static final ContentId STONE = ContentId.parse("branz:mining_stoneworker");
    private static final ContentId SWING = ContentId.parse("branz:mining_efficient_swing");
    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");

    @Test
    void curveLevelsAndMilestonePointsAreDeterministic() {
        LifeSkillProgressionEngine engine = engine();
        LifeSkillSnapshot initial = LifeSkillSnapshot.untrained(MINING, NOW);

        var mutation = engine.award(initial, engine.threshold(5), 12L, NOW);

        assertEquals(5, mutation.after().level());
        assertEquals(2, mutation.after().unspentPoints(), "milestones at levels 2 and 5");
        assertEquals(4, mutation.levelsGained());
        assertEquals(Math.round(75 * Math.pow(3, 1.55)), engine.requiredXp(3));
        assertEquals(75, engine.threshold(2));
        assertEquals(220, engine.threshold(3));
        assertEquals(412, engine.threshold(4));
        assertEquals(643, engine.threshold(5));
        assertEquals(909, engine.threshold(6));
    }

    @Test
    void xpAtLevelCapIsClampedAndCannotOverflow() {
        LifeSkillProgressionEngine engine = engine();
        LifeSkillSnapshot initial = LifeSkillSnapshot.untrained(MINING, NOW);

        var capped = engine.award(initial, Long.MAX_VALUE, 12L, NOW);
        var replayAtCap = engine.award(capped.after(), 10, 12L, NOW);

        assertEquals(100, capped.after().level());
        assertEquals(engine.threshold(100), capped.after().totalXp());
        assertEquals(engine.threshold(100), capped.awardedXp());
        assertEquals(0, replayAtCap.awardedXp());
        assertEquals(capped.after(), replayAtCap.after());
    }

    @Test
    void purchasesCheckLevelPointsRanksAndPrerequisites() {
        LifeSkillProgressionEngine engine = engine();
        LifeSkillSnapshot trained = engine.award(
                LifeSkillSnapshot.untrained(MINING, NOW), engine.threshold(5), 1L, NOW).after();

        var stone = engine.purchase(trained, STONE, NOW).after();
        var swing = engine.purchase(stone, SWING, NOW).after();

        assertEquals(1, swing.rankOf(STONE));
        assertEquals(1, swing.rankOf(SWING));
        assertEquals(0, swing.unspentPoints());
        assertThrows(IllegalStateException.class, () ->
                engine.purchase(LifeSkillSnapshot.untrained(MINING, NOW), SWING, NOW));
    }

    @Test
    void respecRefundsEverySpentPointAtomically() {
        LifeSkillProgressionEngine engine = engine();
        LifeSkillSnapshot trained = engine.award(
                LifeSkillSnapshot.untrained(MINING, NOW), engine.threshold(5), 1L, NOW).after();
        LifeSkillSnapshot purchased = engine.purchase(
                engine.purchase(trained, STONE, NOW).after(), SWING, NOW).after();

        var reset = engine.respec(purchased, NOW).after();

        assertTrue(reset.nodeRanks().isEmpty());
        assertEquals(2, reset.unspentPoints());
    }

    @Test
    void treeRejectsCyclesAndBrokenRanks() {
        LifeSkillNodeDefinition a = node(STONE, Map.of(SWING, 1), 3);
        LifeSkillNodeDefinition b = node(SWING, Map.of(STONE, 1), 2);
        assertThrows(IllegalArgumentException.class, () ->
                new LifeSkillProgressionEngine(skill(), Map.of(STONE, a, SWING, b)));

        LifeSkillNodeDefinition impossible = node(SWING, Map.of(STONE, 4), 2);
        assertThrows(IllegalArgumentException.class, () ->
                new LifeSkillProgressionEngine(skill(),
                        Map.of(STONE, node(STONE, Map.of(), 3), SWING, impossible)));
    }

    private static LifeSkillProgressionEngine engine() {
        return new LifeSkillProgressionEngine(skill(), Map.of(
                STONE, node(STONE, Map.of(), 3),
                SWING, node(SWING, Map.of(STONE, 1), 2)));
    }

    private static LifeSkillDefinition skill() {
        return new LifeSkillDefinition(MINING, "Mining", 100, 75.0, 1.55,
                Set.of(2, 5, 10, 20));
    }

    private static LifeSkillNodeDefinition node(ContentId id,
                                                Map<ContentId, Integer> prerequisites, int maxRank) {
        return new LifeSkillNodeDefinition(id, MINING, id.value(), maxRank, 1, 2,
                prerequisites, new LifeSkillNodeDefinition.Effect(
                "harvest_time_reduction", Set.of("branz:common_mining"), 2.0, 6.0));
    }
}
