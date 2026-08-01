package com.branz.mmorpg.combat.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AilmentEngineTest {
    private final AilmentEngine engine = new AilmentEngine();

    @Test
    void resistanceMultiplierIsClampedAndThresholdConsumesBuildup() {
        AilmentDefinition burn = definition(AilmentReapplication.REFRESH, 1);
        AilmentState state = AilmentState.empty(100);

        AilmentApplication resisted = engine.applyBuildup(burn, state, 100, 0.9, 101);
        AilmentApplication vulnerable =
                engine.applyBuildup(burn, AilmentState.empty(100), 100, -0.9, 101);

        assertEquals(40, resisted.appliedBuildup());
        assertFalse(resisted.thresholdTriggered());
        assertEquals(130, vulnerable.appliedBuildup());
        assertTrue(vulnerable.thresholdTriggered());
        assertEquals(0, vulnerable.state().buildup());
        assertEquals(1, vulnerable.state().tier());
    }

    @Test
    void buildupWaitsForDelayThenDecaysByElapsedServerTicks() {
        AilmentDefinition burn = definition(AilmentReapplication.REFRESH, 1);
        AilmentState applied =
                engine.applyBuildup(burn, AilmentState.empty(100), 50, 0, 100).state();

        AilmentState beforeDelay = engine.advance(burn, applied, 119);
        AilmentState decayed = engine.advance(burn, beforeDelay, 130);

        assertEquals(50, beforeDelay.buildup());
        assertEquals(40, decayed.buildup());
    }

    @Test
    void intensifyCapsTierWhileRefreshAndRejectKeepTheirAuthoredBehavior() {
        AilmentDefinition intensify = definition(AilmentReapplication.INTENSIFY, 2);
        AilmentState first =
                engine.applyBuildup(intensify, AilmentState.empty(100), 100, 0, 100).state();
        AilmentState second = engine.applyBuildup(intensify, first, 100, 0, 110).state();
        AilmentState third = engine.applyBuildup(intensify, second, 100, 0, 120).state();
        AilmentDefinition reject = definition(AilmentReapplication.REJECT, 1);
        AilmentState rejectedFirst =
                engine.applyBuildup(reject, AilmentState.empty(100), 100, 0, 100).state();
        AilmentApplication rejected = engine.applyBuildup(reject, rejectedFirst, 100, 0, 110);

        assertEquals(2, second.tier());
        assertEquals(2, third.tier());
        assertFalse(rejected.activeChanged());
        assertEquals(rejectedFirst.activeUntilTick(), rejected.state().activeUntilTick());
    }

    @Test
    void cleanseTagsAndDeathPersistenceAreExplicit() {
        AilmentDefinition normal = definition(AilmentReapplication.REFRESH, 1);
        AilmentState active =
                engine.applyBuildup(normal, AilmentState.empty(100), 100, 0, 100).state();
        AilmentDefinition corruption =
                new AilmentDefinition(
                        AilmentType.CORRUPTION,
                        100,
                        20,
                        1,
                        100,
                        AilmentReapplication.REFRESH,
                        1,
                        "CORRUPTION",
                        Set.of("SANCTUARY"),
                        AilmentPersistence.PERSIST_THROUGH_DEATH);

        assertFalse(engine.cleanse(normal, active, "WRONG", 101).tier() == 0);
        assertEquals(0, engine.cleanse(normal, active, "REMEDY", 101).tier());
        assertEquals(0, engine.onDeath(normal, active, 101).tier());
        AilmentState corruptionActive =
                engine.applyBuildup(corruption, AilmentState.empty(100), 100, 0, 100).state();
        assertTrue(engine.onDeath(corruption, corruptionActive, 101).activeAt(101));
    }

    private static AilmentDefinition definition(
            AilmentReapplication reapplication, int maximumTier) {
        return new AilmentDefinition(
                AilmentType.BURN,
                100,
                20,
                1,
                100,
                reapplication,
                maximumTier,
                "FIRE",
                Set.of("REMEDY", "WATER"),
                AilmentPersistence.CLEAR_ON_DEATH);
    }
}
