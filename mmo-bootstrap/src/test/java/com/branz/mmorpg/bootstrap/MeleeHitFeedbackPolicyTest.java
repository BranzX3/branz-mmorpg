package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MeleeHitFeedbackPolicyTest {
    @Test
    void zeroNegativeAndInvalidDamageEmitNoFeedback() {
        assertTrue(MeleeHitFeedbackPolicy.forAppliedDamage(0.0).isEmpty());
        assertTrue(MeleeHitFeedbackPolicy.forAppliedDamage(-1.0).isEmpty());
        assertTrue(MeleeHitFeedbackPolicy.forAppliedDamage(Double.NaN).isEmpty());
        assertTrue(MeleeHitFeedbackPolicy.forAppliedDamage(Double.POSITIVE_INFINITY).isEmpty());
    }

    @Test
    void positiveDamageProducesBoundedPresentationFeedback() {
        MeleeHitFeedbackPolicy.MeleeHitFeedbackSpec light =
                MeleeHitFeedbackPolicy.forAppliedDamage(1.0).orElseThrow();
        MeleeHitFeedbackPolicy.MeleeHitFeedbackSpec heavy =
                MeleeHitFeedbackPolicy.forAppliedDamage(200.0).orElseThrow();

        assertEquals(1, light.particleCount());
        assertEquals(8, heavy.particleCount());
        assertEquals(0.75f, light.volume());
        assertTrue(heavy.pitch() >= light.pitch());
        assertTrue(heavy.pitch() <= 1.2f);
    }
}
