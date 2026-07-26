package com.branz.mmorpg.core.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.mastery.MasterySnapshot;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CombatMasteryEngineTest {

    private static final ContentId MASTERY = ContentId.parse("branz:broadsword");
    private static final MasteryDefinition DEFINITION = new MasteryDefinition(
            MASTERY, "Broadsword", MasteryDefinition.Kind.WEAPON_TYPE,
            ContentId.parse("branz:sword"), 100, 100.0, 1.65, 0.20);

    private final CombatMasteryEngine engine = new CombatMasteryEngine(DEFINITION);

    @Test
    void initialCurveAndLevelProgressionMatchSpecification() {
        assertEquals(100L, engine.requiredXp(1));
        assertEquals(314L, engine.requiredXp(2));
        assertEquals(1, engine.levelFor(99));
        assertEquals(2, engine.levelFor(100));
        assertEquals(3, engine.levelFor(414));
    }

    @Test
    void antiFarmMultiplierIsBoundedAndRoundsDown() {
        assertEquals(25L, engine.awardAmount(101, 0.25));
        assertEquals(0L, engine.awardAmount(100, 0.0));
        assertThrows(IllegalArgumentException.class, () -> engine.awardAmount(100, 1.01));
        assertThrows(IllegalArgumentException.class, () -> engine.awardAmount(100, -0.01));
    }

    @Test
    void powerContributionNeverExceedsConfiguredCap() {
        Instant now = Instant.parse("2026-07-26T00:00:00Z");
        assertEquals(0.0, engine.powerBonus(new MasterySnapshot(MASTERY, 1, 0, now)));
        assertEquals(0.20, engine.powerBonus(
                new MasterySnapshot(MASTERY, 100, Long.MAX_VALUE, now)), 0.000001);
    }
}
