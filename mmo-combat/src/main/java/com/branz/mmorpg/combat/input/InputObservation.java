package com.branz.mmorpg.combat.input;

import java.util.Objects;

public record InputObservation(
        long tick,
        SemanticInput input,
        DirectionSnapshot direction,
        String branchFamily,
        InputDeduplicationKey deduplicationKey) {
    public InputObservation {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative");
        }
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(direction, "direction");
        branchFamily = requireText(branchFamily, "branchFamily");
        Objects.requireNonNull(deduplicationKey, "deduplicationKey");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
