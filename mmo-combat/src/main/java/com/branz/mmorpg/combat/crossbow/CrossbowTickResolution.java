package com.branz.mmorpg.combat.crossbow;

import java.util.Objects;

public record CrossbowTickResolution(CrossbowRuntime runtime, CrossbowTickOutcome outcome) {
    public CrossbowTickResolution {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(outcome, "outcome");
    }
}
