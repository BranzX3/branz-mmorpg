package com.branz.mmorpg.combat.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PrimaryAttackInputCoordinatorTest {
    @Test
    void readyPrimaryExecutesImmediately() {
        InputRouter router = new InputRouter();
        Optional<InputRouteOutcome> routed =
                PrimaryAttackInputCoordinator.route(
                        router,
                        100,
                        "PRIMARY",
                        InputRoutingContext.legal(Set.of(SemanticInput.PRIMARY)));

        assertTrue(routed.isPresent());
        assertEquals(InputRouteDecision.EXECUTED, routed.orElseThrow().decision());
        assertEquals(SemanticInput.PRIMARY, routed.orElseThrow().request().input());
    }

    @Test
    void drawingPrimaryUsesExistingOneSlotBuffer() {
        InputRouter router = new InputRouter();
        Optional<InputRouteOutcome> routed =
                PrimaryAttackInputCoordinator.route(
                        router, 200, "PRIMARY", new InputRoutingContext(Set.of(), true));

        assertTrue(routed.isPresent());
        assertEquals(InputRouteDecision.BUFFERED, routed.orElseThrow().decision());
        assertTrue(router.buffered().isPresent());
        assertEquals(SemanticInput.PRIMARY, router.buffered().orElseThrow().input());
    }

    @Test
    void armSwingAndEntityHitEquivalentObservationsAreDeduplicated() {
        InputRouter router = new InputRouter();
        InputRoutingContext legal = InputRoutingContext.legal(Set.of(SemanticInput.PRIMARY));

        Optional<InputRouteOutcome> first =
                PrimaryAttackInputCoordinator.route(router, 300, "PRIMARY", legal);
        Optional<InputRouteOutcome> duplicate =
                PrimaryAttackInputCoordinator.route(router, 301, "PRIMARY", legal);
        Optional<InputRouteOutcome> afterWindow =
                PrimaryAttackInputCoordinator.route(router, 303, "PRIMARY", legal);

        assertTrue(first.isPresent());
        assertTrue(duplicate.isEmpty());
        assertTrue(afterWindow.isPresent());
        assertEquals(InputRouteDecision.EXECUTED, afterWindow.orElseThrow().decision());
    }
}
