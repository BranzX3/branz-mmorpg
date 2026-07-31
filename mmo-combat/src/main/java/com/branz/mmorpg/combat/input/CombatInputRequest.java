package com.branz.mmorpg.combat.input;

import java.util.Objects;

public record CombatInputRequest(
        long sequence,
        long observedTick,
        SemanticInput input,
        DirectionSnapshot direction,
        String branchFamily) {
    public CombatInputRequest {
        if (sequence < 1 || observedTick < 0) {
            throw new IllegalArgumentException("sequence must be positive and tick non-negative");
        }
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(branchFamily, "branchFamily");
        if (branchFamily.isBlank()) {
            throw new IllegalArgumentException("branchFamily must not be blank");
        }
    }

    public int priority() {
        return input.priority(direction);
    }
}
