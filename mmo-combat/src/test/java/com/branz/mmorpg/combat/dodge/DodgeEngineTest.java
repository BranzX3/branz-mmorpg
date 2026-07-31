package com.branz.mmorpg.combat.dodge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.input.DirectionSnapshot;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DodgeEngineTest {
    private final DodgeEngine engine = new DodgeEngine();

    @Test
    void canonicalProfilesUseAuthoredCostsAndOneTickStartup() {
        assertProfile(DodgeLoad.LIGHT, 25, 6, 14, 4.2);
        assertProfile(DodgeLoad.MEDIUM, 30, 4, 16, 3.5);
        assertProfile(DodgeLoad.HEAVY, 35, 2, 18, 2.6);
        assertProfile(DodgeLoad.OVERLOADED, 40, 0, 20, 1.4);
    }

    @Test
    void iframeAvoidsOnlyDodgeableHitsAfterStartup() {
        DodgeRuntime runtime = start(DodgeLoad.MEDIUM, 100, 50);

        assertEquals(DodgePhase.STARTUP, runtime.phaseAt(50));
        assertFalse(engine.avoids(runtime, 50, true));
        for (long tick = 51; tick <= 54; tick++) {
            assertEquals(DodgePhase.INVULNERABLE, runtime.phaseAt(tick));
            assertTrue(engine.avoids(runtime, tick, true));
            assertFalse(engine.avoids(runtime, tick, false));
        }
        assertEquals(DodgePhase.RECOVERY, runtime.phaseAt(55));
        assertFalse(engine.avoids(runtime, 55, true));
        assertEquals(DodgePhase.COMPLETE, runtime.phaseAt(66));
    }

    @Test
    void overloadedDodgeNeverGrantsInvulnerability() {
        DodgeRuntime runtime = start(DodgeLoad.OVERLOADED, 100, 10);

        for (long tick = 10; tick <= 30; tick++) {
            assertFalse(engine.avoids(runtime, tick, true));
        }
    }

    @Test
    void rejectsNeutralInsufficientStaminaAndActiveRecovery() {
        DodgeProfile medium = DodgeProfile.canonical(DodgeLoad.MEDIUM);
        assertFailure(
                engine.start(Optional.empty(), medium, DirectionSnapshot.NEUTRAL, 100, 0),
                DodgeErrorCode.NEUTRAL_DIRECTION);
        assertFailure(
                engine.start(Optional.empty(), medium, DirectionSnapshot.FORWARD, 29, 0),
                DodgeErrorCode.NO_STAMINA);
        DodgeRuntime active = start(DodgeLoad.MEDIUM, 100, 0);
        assertFailure(
                engine.start(Optional.of(active), medium, DirectionSnapshot.BACK, 100, 15),
                DodgeErrorCode.ALREADY_DODGING);
        assertTrue(
                engine.start(Optional.of(active), medium, DirectionSnapshot.BACK, 100, 16)
                        .isSuccess());
    }

    private void assertProfile(
            DodgeLoad load, int stamina, int iframes, int total, double distance) {
        DodgeProfile profile = DodgeProfile.canonical(load);
        assertEquals(stamina, profile.staminaCost());
        assertEquals(iframes, profile.iframeTicks());
        assertEquals(total, profile.totalTicks());
        assertEquals(distance, profile.travelDistance());
    }

    private DodgeRuntime start(DodgeLoad load, int stamina, long tick) {
        Result<DodgeRuntime, DodgeErrorCode> result =
                engine.start(
                        Optional.empty(),
                        DodgeProfile.canonical(load),
                        DirectionSnapshot.FORWARD,
                        stamina,
                        tick);
        assertTrue(result.isSuccess());
        return ((Result.Success<DodgeRuntime, DodgeErrorCode>) result).value();
    }

    private static void assertFailure(
            Result<DodgeRuntime, DodgeErrorCode> result, DodgeErrorCode expected) {
        assertTrue(result instanceof Result.Failure<?, ?>);
        assertEquals(expected, ((Result.Failure<DodgeRuntime, DodgeErrorCode>) result).error());
    }
}
