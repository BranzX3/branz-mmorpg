package com.branz.mmorpg.combat.status;

import java.util.Objects;

public record AilmentApplication(
        AilmentState state,
        double appliedBuildup,
        boolean thresholdTriggered,
        boolean activeChanged) {
    public AilmentApplication {
        Objects.requireNonNull(state, "state");
        if (!Double.isFinite(appliedBuildup) || appliedBuildup < 0) {
            throw new IllegalArgumentException("appliedBuildup must not be negative");
        }
    }
}
