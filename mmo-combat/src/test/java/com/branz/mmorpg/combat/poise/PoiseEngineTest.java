package com.branz.mmorpg.combat.poise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.combat.cc.CcSeverity;
import org.junit.jupiter.api.Test;

class PoiseEngineTest {
    private final PoiseEngine engine = new PoiseEngine(PoiseProfile.trainingPlayer());

    @Test
    void accumulationResistsBelowThresholdThenTriggersAndClears() {
        PoiseRuntime runtime = PoiseRuntime.initial(0);
        PoiseResolution first = engine.apply(runtime, 0, 10, 1, CcSeverity.FLINCH);
        assertTrue(first.resisted());
        assertEquals(10, first.runtime().accumulated());
        PoiseResolution second = engine.apply(first.runtime(), 5, 19, 1, CcSeverity.STAGGER);
        assertTrue(second.resisted());
        PoiseResolution trigger = engine.apply(second.runtime(), 9, 1, 1, CcSeverity.STAGGER);
        assertFalse(trigger.resisted());
        assertEquals(CcSeverity.STAGGER, trigger.triggeredSeverity().orElseThrow());
        assertEquals(0, trigger.runtime().accumulated());
    }

    @Test
    void hyperArmorScalesImpactAndAccumulationDecaysAfterTenTicks() {
        PoiseRuntime runtime = PoiseRuntime.initial(0);
        runtime = engine.apply(runtime, 0, 20, 0.5, CcSeverity.FLINCH).runtime();
        assertEquals(10, runtime.accumulated());
        assertEquals(10, engine.tick(runtime, 9).accumulated());
        assertEquals(1, engine.tick(runtime, 30).accumulated());
    }
}
