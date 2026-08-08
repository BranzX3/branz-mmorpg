package com.branz.mmorpg.combat.input;

import com.branz.mmorpg.api.result.Result;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Owns observation deduplication and one-frame routing for the physical primary attack. Both Bukkit
 * arm-swing and direct entity-hit ingress converge here through the controller's single
 * routePrimaryAttack path.
 */
public final class PrimaryAttackInputCoordinator {
    private static final InputDeduplicationKey PHYSICAL_ATTACK =
            new InputDeduplicationKey("MAIN_HAND", "ATTACK");

    private PrimaryAttackInputCoordinator() {}

    public static Optional<InputRouteOutcome> route(
            InputRouter router,
            long tick,
            String branchFamily,
            InputRoutingContext routingContext) {
        Objects.requireNonNull(router, "router");
        Objects.requireNonNull(branchFamily, "branchFamily");
        Objects.requireNonNull(routingContext, "routingContext");
        Result<CombatInputRequest, InputRejectionCode> observed =
                router.observe(
                        new InputObservation(
                                tick,
                                SemanticInput.PRIMARY,
                                DirectionSnapshot.NEUTRAL,
                                branchFamily,
                                PHYSICAL_ATTACK));
        if (!(observed instanceof Result.Success<CombatInputRequest, InputRejectionCode> input)) {
            return Optional.empty();
        }
        Result<InputRouteOutcome, InputRejectionCode> routed =
                router.routeFrame(List.of(input.value()), routingContext);
        if (routed instanceof Result.Success<InputRouteOutcome, InputRejectionCode> success) {
            return Optional.of(success.value());
        }
        return Optional.empty();
    }
}
