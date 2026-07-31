package com.branz.mmorpg.combat.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.TestMoveFixtures;
import com.branz.mmorpg.combat.action.ActionPhase;
import com.branz.mmorpg.combat.action.ActionTimeline;
import com.branz.mmorpg.combat.action.CombatResources;
import com.branz.mmorpg.combat.cc.CcEngine;
import com.branz.mmorpg.combat.cc.CcRequest;
import com.branz.mmorpg.combat.cc.CcRuntime;
import com.branz.mmorpg.combat.cc.CcSeverity;
import com.branz.mmorpg.combat.guard.GuardEngine;
import com.branz.mmorpg.combat.guard.GuardHitOutcome;
import com.branz.mmorpg.combat.guard.GuardHitRequest;
import com.branz.mmorpg.combat.guard.GuardProfile;
import com.branz.mmorpg.combat.guard.GuardResolution;
import com.branz.mmorpg.combat.guard.GuardRuntime;
import com.branz.mmorpg.combat.hitbox.CombatVector;
import com.branz.mmorpg.combat.input.CombatInputPolicy;
import com.branz.mmorpg.combat.input.CombatInputRequest;
import com.branz.mmorpg.combat.input.DirectionSnapshot;
import com.branz.mmorpg.combat.input.InputDeduplicationKey;
import com.branz.mmorpg.combat.input.InputLatencySimulator;
import com.branz.mmorpg.combat.input.InputObservation;
import com.branz.mmorpg.combat.input.InputPolicyContext;
import com.branz.mmorpg.combat.input.InputRouteDecision;
import com.branz.mmorpg.combat.input.InputRouteOutcome;
import com.branz.mmorpg.combat.input.InputRouter;
import com.branz.mmorpg.combat.input.InputRoutingContext;
import com.branz.mmorpg.combat.input.LatencyInputEmission;
import com.branz.mmorpg.combat.input.SemanticInput;
import com.branz.mmorpg.combat.state.ActionState;
import com.branz.mmorpg.combat.state.EngagementState;
import com.branz.mmorpg.combat.state.UiState;
import com.branz.mmorpg.combat.state.WeaponState;
import com.branz.mmorpg.combat.trace.ActionTimelineSimulator;
import com.branz.mmorpg.combat.trace.CombatTrace;
import com.branz.mmorpg.combat.weapon.SelectedHotbarSlot;
import com.branz.mmorpg.combat.weapon.WeaponTransitionMachine;
import com.branz.mmorpg.combat.weapon.WeaponTransitionSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** End-to-end deterministic acceptance kit for the first training weapon runtime. */
class TrainingWeaponAcceptanceKitTest {
    @Test
    void delayedOpenerBuffersDuringDrawThenCompletesAndReplaysExactly() {
        WeaponTransitionMachine weapons = new WeaponTransitionMachine(6, 4);
        WeaponTransitionSnapshot weapon =
                success(
                        weapons.select(
                                WeaponTransitionSnapshot.initial(),
                                SelectedHotbarSlot.combatWeapon(0)));
        InputRouter router = new InputRouter();
        InputObservation delayedAttack =
                new InputLatencySimulator()
                        .deliver(
                                List.of(
                                        emission(
                                                1,
                                                0,
                                                2,
                                                0,
                                                SemanticInput.PRIMARY,
                                                DirectionSnapshot.NEUTRAL)))
                        .getFirst()
                        .observations()
                        .getFirst();

        InputRouteOutcome buffered = null;
        for (long tick = 1; tick <= 6; tick++) {
            weapon = weapons.tick(weapon);
            if (tick == delayedAttack.tick()) {
                CombatInputRequest request = success(router.observe(delayedAttack));
                buffered =
                        success(
                                router.routeFrame(
                                        List.of(request), routingContext(weapon.state())));
            }
        }

        assertEquals(InputRouteDecision.BUFFERED, buffered.decision());
        assertEquals(WeaponState.READY, weapon.state());
        InputRouteOutcome opener = success(router.pollBuffered(6, routingContext(weapon.state())));
        assertEquals(InputRouteDecision.EXECUTED, opener.decision());

        CombatResources initial = CombatResources.full(1000, 100, 0);
        ActionTimeline timeline =
                success(ActionTimeline.start(TestMoveFixtures.trainingSlash(), initial));
        while (!timeline.phase().terminal()) {
            timeline = success(timeline.advance());
        }
        CombatTrace trace =
                new CombatTrace(
                        "content.acceptance",
                        TestMoveFixtures.trainingSlash().id(),
                        initial,
                        List.of(),
                        timeline.trace(),
                        timeline.resources(),
                        timeline.phase());
        assertEquals(ActionPhase.COMPLETE, timeline.phase());
        assertEquals(88, timeline.resources().stamina());
        assertTrue(
                new ActionTimelineSimulator()
                        .replay(trace, TestMoveFixtures.moveEngine())
                        .isSuccess());
    }

    @Test
    void jitteredSameFrameStillPrioritizesDirectionalDodgeOverAttack() {
        List<InputObservation> observations =
                new InputLatencySimulator()
                        .deliver(
                                List.of(
                                        emission(
                                                1,
                                                0,
                                                1,
                                                0,
                                                SemanticInput.PRIMARY,
                                                DirectionSnapshot.NEUTRAL),
                                        emission(
                                                2,
                                                1,
                                                1,
                                                -1,
                                                SemanticInput.DODGE,
                                                DirectionSnapshot.RIGHT)))
                        .getFirst()
                        .observations();
        InputRouter router = new InputRouter();
        ArrayList<CombatInputRequest> requests = new ArrayList<>();
        observations.forEach(observation -> requests.add(success(router.observe(observation))));

        InputRouteOutcome routed =
                success(
                        router.routeFrame(
                                requests,
                                InputRoutingContext.legal(
                                        Set.of(SemanticInput.PRIMARY, SemanticInput.DODGE))));

        assertEquals(SemanticInput.DODGE, routed.request().input());
    }

    @Test
    void delayedGuardUsesArrivalTickAndHardCcCancelsPreCommitAction() {
        InputObservation delayedGuard =
                new InputLatencySimulator()
                        .deliver(
                                List.of(
                                        emission(
                                                1,
                                                0,
                                                2,
                                                0,
                                                SemanticInput.DEFENSIVE_RESPONSE,
                                                DirectionSnapshot.NEUTRAL)))
                        .getFirst()
                        .observations()
                        .getFirst();
        InputRouter router = new InputRouter();
        CombatInputRequest guardRequest = success(router.observe(delayedGuard));
        assertEquals(
                InputRouteDecision.EXECUTED,
                success(
                                router.routeFrame(
                                        List.of(guardRequest),
                                        InputRoutingContext.legal(
                                                Set.of(SemanticInput.DEFENSIVE_RESPONSE))))
                        .decision());

        GuardEngine guards = new GuardEngine(GuardProfile.trainingWeapon());
        GuardRuntime guard =
                success(
                        guards.start(
                                GuardRuntime.initial(guards.profile(), 0), delayedGuard.tick()));
        GuardResolution perfect = guards.resolve(guard, 6, guardHit());
        GuardResolution normal = guards.resolve(perfect.runtime(), 7, guardHit());
        assertEquals(GuardHitOutcome.PERFECT_GUARD, perfect.outcome());
        assertEquals(GuardHitOutcome.GUARDED, normal.outcome());

        ActionTimeline timeline =
                success(
                        ActionTimeline.start(
                                TestMoveFixtures.trainingSlash(),
                                CombatResources.full(1000, 100, 0)));
        timeline = success(timeline.advance());
        timeline = success(timeline.advance());
        assertTrue(
                new CcEngine()
                        .apply(
                                CcRuntime.initial(0),
                                2,
                                new CcRequest(CcSeverity.HEAVY_STAGGER, 24, false, false))
                        .applied());
        timeline = success(timeline.cancel("CC_HEAVY_STAGGER"));
        assertEquals(ActionPhase.CANCELLED, timeline.phase());
        assertEquals(100, timeline.resources().stamina());
        assertTrue(
                new CombatInputPolicy()
                                .resolve(
                                        com.branz.mmorpg.combat.input.ClientAction.ATTACK,
                                        new InputPolicyContext(
                                                EngagementState.ENGAGED,
                                                WeaponState.READY,
                                                ActionState.STAGGERED,
                                                UiState.NONE,
                                                false,
                                                DirectionSnapshot.NEUTRAL))
                        instanceof Result.Failure);
    }

    private static InputRoutingContext routingContext(WeaponState weapon) {
        return new CombatInputPolicy()
                .routingContext(
                        new InputPolicyContext(
                                EngagementState.ENGAGED,
                                weapon,
                                ActionState.IDLE,
                                UiState.NONE,
                                false,
                                DirectionSnapshot.NEUTRAL),
                        false);
    }

    private static LatencyInputEmission emission(
            long sequence,
            long emittedTick,
            int latency,
            int jitter,
            SemanticInput input,
            DirectionSnapshot direction) {
        return new LatencyInputEmission(
                sequence,
                emittedTick,
                latency,
                jitter,
                input,
                direction,
                input.name(),
                new InputDeduplicationKey("MAIN_HAND", sequence + ":" + input));
    }

    private static GuardHitRequest guardHit() {
        return new GuardHitRequest(
                100, 10, true, true, new CombatVector(0, 0, 1), new CombatVector(0, 0, 1), 100);
    }

    private static <T, E extends com.branz.mmorpg.api.result.ErrorCode> T success(
            Result<T, E> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<T, E>) result).value();
    }
}
