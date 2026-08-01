package com.branz.mmorpg.combat.resource;

import java.util.Objects;

public record FlaskPreparation(
        FlaskState state, int infusionStockConsumed, int mercyChargesGranted) {
    public FlaskPreparation {
        Objects.requireNonNull(state, "state");
        if (infusionStockConsumed < 0 || mercyChargesGranted < 0) {
            throw new IllegalArgumentException("preparation costs must not be negative");
        }
    }
}
