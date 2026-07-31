package com.branz.mmorpg.combat.bow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BowDrawEngineTest {
    private final BowDrawEngine bows =
            new BowDrawEngine(new BowDrawProfile(5, 20, 60, 4, 0.55, 0.5, 0.2));

    @Test
    void minimumAndFullDrawReleaseBoundariesAreExact() {
        BowDrawRuntime started = bows.start(100);

        BowReleaseResolution early = bows.release(started, 104, 100);
        BowReleaseResolution minimum = bows.release(started, 105, 100);
        BowReleaseResolution full = bows.release(started, 120, 100);

        assertEquals(BowReleaseOutcome.TOO_EARLY, early.outcome());
        assertTrue(early.shot().isEmpty());
        assertEquals(0, minimum.shot().orElseThrow().drawRatio());
        assertEquals(0.55, minimum.shot().orElseThrow().velocityMultiplier());
        assertEquals(1, full.shot().orElseThrow().drawRatio());
        assertEquals(1, full.shot().orElseThrow().velocityMultiplier());
        assertEquals(0.2, full.shot().orElseThrow().penetrationPercentage());
    }

    @Test
    void fullDrawHoldsFreeForThreeSecondsThenDrainsFourPerSecond() {
        BowDrawRuntime started = bows.start(0);
        BowTickResolution free = bows.tick(started, 79, 100);
        BowTickResolution strainStart = bows.tick(free.runtime(), 80, 100);
        BowTickResolution fifthStrainTick = bows.tick(strainStart.runtime(), 84, 100);

        assertEquals(BowDrawPhase.FULL_DRAW, free.runtime().phase());
        assertEquals(0, free.staminaSpent());
        assertEquals(BowDrawPhase.STRAINED, strainStart.runtime().phase());
        assertEquals(0, strainStart.staminaSpent());
        assertEquals(1, fifthStrainTick.staminaSpent());
        assertFalse(fifthStrainTick.loweredForExhaustion());
    }

    @Test
    void zeroOrFinalStaminaLowersWithoutFiring() {
        BowDrawRuntime strained = bows.tick(bows.start(0), 80, 1).runtime();

        BowReleaseResolution zero = bows.release(strained, 81, 0);
        BowTickResolution finalPoint = bows.tick(strained, 84, 1);

        assertEquals(BowReleaseOutcome.EXHAUSTED, zero.outcome());
        assertTrue(zero.shot().isEmpty());
        assertTrue(finalPoint.loweredForExhaustion());
        assertEquals(1, finalPoint.staminaSpent());
    }

    @Test
    void tickRegressionAndInvalidProfilesFailClosed() {
        BowDrawRuntime runtime = bows.start(10);

        assertThrows(IllegalArgumentException.class, () -> bows.tick(runtime, 9, 100));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BowDrawProfile(5, 5, 60, 4, 0.55, 0.5, 0.2));
    }
}
