package com.branz.mmorpg.combat.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InputRouterTest {
    @Test
    void sameFramePriorityIsDeterministicRegardlessOfObservationOrder() {
        List<CombatInputRequest> requests =
                List.of(
                        request(1, SemanticInput.PRIMARY, DirectionSnapshot.FORWARD),
                        request(2, SemanticInput.SIGNATURE, DirectionSnapshot.NEUTRAL),
                        request(3, SemanticInput.DODGE, DirectionSnapshot.RIGHT),
                        request(4, SemanticInput.DEFENSIVE_RESPONSE, DirectionSnapshot.NEUTRAL));
        InputRoutingContext context =
                InputRoutingContext.legal(
                        Set.of(
                                SemanticInput.PRIMARY,
                                SemanticInput.SIGNATURE,
                                SemanticInput.DODGE,
                                SemanticInput.DEFENSIVE_RESPONSE));

        for (int seed = 0; seed < 100; seed++) {
            ArrayList<CombatInputRequest> shuffled = new ArrayList<>(requests);
            Collections.shuffle(shuffled, new java.util.Random(seed));
            InputRouteOutcome outcome = success(new InputRouter().routeFrame(shuffled, context));
            assertEquals(SemanticInput.DODGE, outcome.request().input());
        }
    }

    @Test
    void duplicateBukkitAndPacketObservationCollapsesWithinTwoTicks() {
        InputRouter router = new InputRouter();
        InputDeduplicationKey key = new InputDeduplicationKey("MAIN_HAND", "USE");
        assertTrue(
                router.observe(observation(10, SemanticInput.SECONDARY, "SECONDARY", key))
                        .isSuccess());

        Result<CombatInputRequest, InputRejectionCode> duplicate =
                router.observe(observation(12, SemanticInput.SECONDARY, "SECONDARY", key));
        assertEquals(
                InputRejectionCode.DUPLICATE_OBSERVATION,
                ((Result.Failure<CombatInputRequest, InputRejectionCode>) duplicate).error());
        assertTrue(
                router.observe(observation(13, SemanticInput.SECONDARY, "SECONDARY", key))
                        .isSuccess());
    }

    @Test
    void oneSlotBufferReplacesOnlyHigherPriorityOrSameBranchAndExpires() {
        InputRouter router = new InputRouter();
        InputRoutingContext queueWindow = new InputRoutingContext(Set.of(), true);

        InputRouteOutcome first =
                success(
                        router.routeFrame(
                                List.of(
                                        request(
                                                1,
                                                SemanticInput.PRIMARY,
                                                DirectionSnapshot.NEUTRAL)),
                                queueWindow));
        assertEquals(InputRouteDecision.BUFFERED, first.decision());

        Result<InputRouteOutcome, InputRejectionCode> retained =
                router.routeFrame(
                        List.of(request(2, SemanticInput.SECONDARY, DirectionSnapshot.NEUTRAL)),
                        queueWindow);
        assertEquals(
                InputRejectionCode.BUFFER_OCCUPIED,
                ((Result.Failure<InputRouteOutcome, InputRejectionCode>) retained).error());
        assertEquals(SemanticInput.PRIMARY, router.buffered().orElseThrow().input());

        InputRouteOutcome higher =
                success(
                        router.routeFrame(
                                List.of(
                                        request(
                                                3,
                                                SemanticInput.SECONDARY,
                                                DirectionSnapshot.FORWARD)),
                                queueWindow));
        assertEquals(InputRouteDecision.BUFFERED, higher.decision());
        assertEquals(SemanticInput.SECONDARY, router.buffered().orElseThrow().input());

        InputRouteOutcome refreshed =
                success(
                        router.routeFrame(
                                List.of(
                                        new CombatInputRequest(
                                                4,
                                                5,
                                                SemanticInput.SECONDARY,
                                                DirectionSnapshot.BACK,
                                                "SECONDARY")),
                                queueWindow));
        assertEquals(InputRouteDecision.BUFFER_REFRESHED, refreshed.decision());

        Result<InputRouteOutcome, InputRejectionCode> expired =
                router.pollBuffered(18, InputRoutingContext.legal(Set.of(SemanticInput.SECONDARY)));
        assertEquals(
                InputRejectionCode.BUFFER_EXPIRED,
                ((Result.Failure<InputRouteOutcome, InputRejectionCode>) expired).error());
        assertTrue(router.buffered().isEmpty());
    }

    @Test
    void directionalSamplingUsesFourWayDominantAxisAndForwardWinsTies() {
        assertEquals(DirectionSnapshot.NEUTRAL, DirectionSnapshot.fromAxes(0.0, 0.0));
        assertEquals(DirectionSnapshot.FORWARD, DirectionSnapshot.fromAxes(1.0, 1.0));
        assertEquals(DirectionSnapshot.BACK, DirectionSnapshot.fromAxes(-0.8, 0.2));
        assertEquals(DirectionSnapshot.LEFT, DirectionSnapshot.fromAxes(0.2, 0.8));
        assertEquals(DirectionSnapshot.RIGHT, DirectionSnapshot.fromAxes(0.2, -0.8));
    }

    private static CombatInputRequest request(
            long sequence, SemanticInput input, DirectionSnapshot direction) {
        return new CombatInputRequest(sequence, 0, input, direction, input.name());
    }

    private static InputObservation observation(
            long tick, SemanticInput input, String branch, InputDeduplicationKey key) {
        return new InputObservation(tick, input, DirectionSnapshot.NEUTRAL, branch, key);
    }

    private static InputRouteOutcome success(Result<InputRouteOutcome, InputRejectionCode> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<InputRouteOutcome, InputRejectionCode>) result).value();
    }
}
