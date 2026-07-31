package com.branz.mmorpg.combat.input;

import java.util.Objects;

public record InputRouteOutcome(InputRouteDecision decision, CombatInputRequest request) {
    public InputRouteOutcome {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(request, "request");
    }
}
