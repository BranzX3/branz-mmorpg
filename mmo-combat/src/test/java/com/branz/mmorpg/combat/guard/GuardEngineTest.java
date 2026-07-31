package com.branz.mmorpg.combat.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.hitbox.CombatVector;
import org.junit.jupiter.api.Test;

class GuardEngineTest {
    private final GuardEngine engine = new GuardEngine(GuardProfile.trainingWeapon());

    @Test
    void fourTickPerfectWindowPrecedesNormalWeaponGuard() {
        GuardRuntime guard = start(0);
        for (long tick = 0; tick <= 3; tick++) {
            GuardResolution result = engine.resolve(guard, tick, frontHit(100, 10, 100));
            assertEquals(GuardHitOutcome.PERFECT_GUARD, result.outcome());
            assertEquals(0, result.finalDamage());
            assertEquals(5, result.staminaSpent());
        }

        GuardResolution normal = engine.resolve(guard, 4, frontHit(100, 10, 100));
        assertEquals(GuardHitOutcome.GUARDED, normal.outcome());
        assertEquals(20, normal.finalDamage(), 1.0e-9);
        assertEquals(10, normal.staminaSpent());
    }

    @Test
    void guardRejectsRearUnblockableAndUnaffordableHits() {
        GuardRuntime guard = start(0);
        assertEquals(
                GuardHitOutcome.OUTSIDE_CONE,
                engine.resolve(guard, 4, rearHit(100, 10, 100)).outcome());
        assertEquals(
                GuardHitOutcome.UNGUARDED,
                engine.resolve(guard, 4, frontHit(100, 10, 100, false, false)).outcome());
        assertEquals(
                GuardHitOutcome.EXHAUSTED,
                engine.resolve(guard, 4, frontHit(100, 10, 9)).outcome());

        GuardHitRequest notPerfect = frontHit(100, 10, 100, true, false);
        assertEquals(GuardHitOutcome.GUARDED, engine.resolve(guard, 0, notPerfect).outcome());
    }

    @Test
    void defaultDirectionalConeIncludesSixtyDegreesAndRejectsBeyondIt() {
        GuardRuntime guard = start(0);
        assertEquals(GuardHitOutcome.GUARDED, engine.resolve(guard, 4, angledHit(60)).outcome());
        assertEquals(
                GuardHitOutcome.OUTSIDE_CONE, engine.resolve(guard, 4, angledHit(60.01)).outcome());
    }

    @Test
    void depletionBreaksThenRestoresStabilityAndRecoveryUsesHoldRate() {
        GuardRuntime guard = start(0);
        GuardResolution broken = engine.resolve(guard, 4, frontHit(100, 100, 100));
        assertEquals(GuardHitOutcome.GUARD_BREAK, broken.outcome());
        assertEquals(GuardPhase.BROKEN, engine.phaseAt(broken.runtime(), 27));

        GuardRuntime restored = engine.tick(broken.runtime(), 28);
        assertEquals(GuardPhase.INACTIVE, engine.phaseAt(restored, 28));
        assertEquals(35, restored.stability());
        restored = engine.tick(restored, 38);
        assertEquals(40, restored.stability());

        GuardRuntime active = start(100);
        GuardResolution pressured = engine.resolve(active, 104, frontHit(1, 20, 100));
        GuardRuntime recovered = engine.tick(pressured.runtime(), 136);
        assertEquals(81, recovered.stability());
    }

    @Test
    void releaseAndRestartCreateANewPerfectWindow() {
        GuardRuntime guard = start(10);
        GuardRuntime released = success(engine.release(guard, 20));
        assertEquals(GuardPhase.INACTIVE, engine.phaseAt(released, 20));
        GuardRuntime restarted = success(engine.start(released, 30));
        assertEquals(GuardPhase.PERFECT, engine.phaseAt(restarted, 30));
    }

    private GuardRuntime start(long tick) {
        return success(engine.start(GuardRuntime.initial(engine.profile(), tick), tick));
    }

    private static GuardHitRequest frontHit(double damage, double pressure, int stamina) {
        return frontHit(damage, pressure, stamina, true, true);
    }

    private static GuardHitRequest frontHit(
            double damage,
            double pressure,
            int stamina,
            boolean guardable,
            boolean perfectGuardable) {
        return new GuardHitRequest(
                damage,
                pressure,
                guardable,
                perfectGuardable,
                new CombatVector(0, 0, 1),
                new CombatVector(0, 0, 1),
                stamina);
    }

    private static GuardHitRequest rearHit(double damage, double pressure, int stamina) {
        return new GuardHitRequest(
                damage,
                pressure,
                true,
                true,
                new CombatVector(0, 0, 1),
                new CombatVector(0, 0, -1),
                stamina);
    }

    private static GuardHitRequest angledHit(double degrees) {
        double radians = Math.toRadians(degrees);
        return new GuardHitRequest(
                100,
                10,
                true,
                true,
                new CombatVector(0, 0, 1),
                new CombatVector(Math.sin(radians), 0, Math.cos(radians)),
                100);
    }

    private static GuardRuntime success(Result<GuardRuntime, GuardErrorCode> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<GuardRuntime, GuardErrorCode>) result).value();
    }
}
