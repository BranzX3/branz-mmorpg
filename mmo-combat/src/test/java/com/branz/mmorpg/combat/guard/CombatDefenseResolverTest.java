package com.branz.mmorpg.combat.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.dodge.DodgeEngine;
import com.branz.mmorpg.combat.dodge.DodgeLoad;
import com.branz.mmorpg.combat.dodge.DodgeProfile;
import com.branz.mmorpg.combat.dodge.DodgeRuntime;
import com.branz.mmorpg.combat.hitbox.CombatVector;
import com.branz.mmorpg.combat.input.DirectionSnapshot;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CombatDefenseResolverTest {
    private final DodgeEngine dodges = new DodgeEngine();
    private final GuardEngine guards = new GuardEngine(GuardProfile.trainingWeapon());
    private final CombatDefenseResolver defense = new CombatDefenseResolver(dodges, guards);
    private final GuardRuntime guard =
            success(guards.start(GuardRuntime.initial(guards.profile(), 0), 0));
    private final DodgeRuntime dodge =
            new DodgeRuntime(
                    DodgeProfile.canonical(DodgeLoad.MEDIUM), DirectionSnapshot.FORWARD, 0);

    @Test
    void dodgeWinsBeforePerfectGuardOnTheSameInvulnerableTick() {
        CombatDefenseResolution result = defense.resolve(Optional.of(dodge), guard, 1, true, hit());

        assertEquals(CombatDefenseOutcome.DODGED, result.outcome());
        assertEquals(100, result.guardRuntime().stability());
        assertEquals(0, result.staminaSpent());
    }

    @Test
    void startupOrUndodgeableHitFallsThroughToPerfectGuard() {
        assertEquals(
                CombatDefenseOutcome.PERFECT_GUARD,
                defense.resolve(Optional.of(dodge), guard, 0, true, hit()).outcome());
        assertEquals(
                CombatDefenseOutcome.PERFECT_GUARD,
                defense.resolve(Optional.of(dodge), guard, 1, false, hit()).outcome());
    }

    @Test
    void ordinaryHitFallsThroughToNormalGuardThenHit() {
        CombatDefenseResolution guarded = defense.resolve(Optional.empty(), guard, 4, true, hit());
        assertEquals(CombatDefenseOutcome.GUARDED, guarded.outcome());

        GuardRuntime inactive = success(guards.release(guard, 4));
        assertEquals(
                CombatDefenseOutcome.HIT,
                defense.resolve(Optional.empty(), inactive, 4, true, hit()).outcome());
    }

    private static GuardHitRequest hit() {
        return new GuardHitRequest(
                100, 10, true, true, new CombatVector(0, 0, 1), new CombatVector(0, 0, 1), 100);
    }

    private static GuardRuntime success(Result<GuardRuntime, GuardErrorCode> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<GuardRuntime, GuardErrorCode>) result).value();
    }
}
