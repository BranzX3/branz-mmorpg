package com.branz.mmorpg.combat.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class CombatHealthEngineTest {
    private final CombatHealthEngine health =
            new CombatHealthEngine(CombatHealthProfile.trainingPlayer());

    @Test
    void damageClampsAtZeroAndLethalTransitionOccursExactlyOnce() {
        CombatHealthRuntime runtime =
                CombatHealthRuntime.full(CombatHealthProfile.trainingPlayer(), 0);

        CombatHealthResolution first = health.damage(runtime, 5, 250.5);
        CombatHealthResolution lethal = health.damage(first.runtime(), 6, 900);
        CombatHealthResolution repeated = health.damage(lethal.runtime(), 7, 100);

        assertEquals(749.5, first.runtime().current());
        assertEquals(250.5, first.appliedAmount());
        assertFalse(first.lethalNow());
        assertEquals(0, lethal.runtime().current());
        assertEquals(749.5, lethal.appliedAmount());
        assertTrue(lethal.lethalNow());
        assertEquals(0, repeated.appliedAmount());
        assertFalse(repeated.lethalNow());
    }

    @Test
    void openWorldRecoveryStartsAtFourHundredTicksAndStopsAtEightyPercent() {
        CombatHealthRuntime damaged =
                health.damage(
                                CombatHealthRuntime.full(CombatHealthProfile.trainingPlayer(), 0),
                                10,
                                500)
                        .runtime();

        assertEquals(500, health.tickOpenWorld(damaged, 409, true).current());
        CombatHealthRuntime first = health.tickOpenWorld(damaged, 410, true);
        assertEquals(500.25, first.current());
        assertEquals(500.25, health.tickOpenWorld(first, 510, false).current());
        assertEquals(800, health.tickOpenWorld(first, 2000, true).current());
    }

    @Test
    void healingCannotReviveAndRespawnRestoresProfileAmount() {
        CombatHealthRuntime dead =
                health.kill(CombatHealthRuntime.full(CombatHealthProfile.trainingPlayer(), 0), 2);

        assertEquals(0, health.heal(dead, 3, 1000).runtime().current());
        CombatHealthRuntime respawned = health.respawn(dead, 4);
        assertEquals(1000, respawned.current());
        assertFalse(respawned.dead());
        assertThrows(IllegalStateException.class, () -> health.respawn(respawned, 5));
    }

    @Test
    void invalidProfileRuntimeAmountAndTickFailClosed() {
        CombatHealthRuntime full =
                CombatHealthRuntime.full(CombatHealthProfile.trainingPlayer(), 10);

        assertThrows(IllegalArgumentException.class, () -> new CombatHealthProfile(0, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CombatHealthRuntime(10, 11, 10));
        assertThrows(IllegalArgumentException.class, () -> health.damage(full, 11, -1));
        assertThrows(IllegalArgumentException.class, () -> health.heal(full, 11, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> health.tickOpenWorld(full, 9, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> health.damage(new CombatHealthRuntime(1001, -1, 10), 11, 1));
    }

    @Test
    void oneHundredThousandSeededTransitionsRemainBoundedAndDeterministic() {
        CombatHealthRuntime first = simulate(0xB12A4L);
        CombatHealthRuntime second = simulate(0xB12A4L);

        assertEquals(first, second);
        assertTrue(first.current() >= 0 && first.current() <= health.profile().maximum());
    }

    private CombatHealthRuntime simulate(long seed) {
        Random random = new Random(seed);
        CombatHealthRuntime runtime =
                CombatHealthRuntime.full(CombatHealthProfile.trainingPlayer(), 0);
        for (long tick = 1; tick <= 100_000; tick++) {
            int operation = random.nextInt(100);
            if (runtime.dead()) {
                if (operation < 5) {
                    runtime = health.respawn(runtime, tick);
                } else {
                    runtime = health.tickOpenWorld(runtime, tick, true);
                }
            } else if (operation < 12) {
                runtime = health.damage(runtime, tick, random.nextDouble(180)).runtime();
            } else if (operation < 16) {
                runtime = health.heal(runtime, tick, random.nextDouble(80)).runtime();
            } else {
                runtime = health.tickOpenWorld(runtime, tick, random.nextBoolean());
            }
            assertTrue(runtime.current() >= 0 && runtime.current() <= health.profile().maximum());
            assertEquals(tick, runtime.lastTick());
        }
        return runtime;
    }
}
