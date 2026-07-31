package com.branz.mmorpg.combat.input;

import java.util.Objects;

public record InputDeduplicationKey(String hand, String action) {
    public InputDeduplicationKey {
        hand = requireText(hand, "hand");
        action = requireText(action, "action");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
