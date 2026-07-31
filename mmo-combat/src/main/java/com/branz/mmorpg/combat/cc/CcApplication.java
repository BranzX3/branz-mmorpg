package com.branz.mmorpg.combat.cc;

import java.util.Objects;

public record CcApplication(
        CcApplicationOutcome outcome, CcRuntime runtime, int effectiveDurationTicks) {
    public CcApplication {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(runtime, "runtime");
        if (effectiveDurationTicks < 0) {
            throw new IllegalArgumentException("effective CC duration must not be negative");
        }
    }

    public boolean applied() {
        return outcome == CcApplicationOutcome.APPLIED
                || outcome == CcApplicationOutcome.REPLACED
                || outcome == CcApplicationOutcome.CONTINUED;
    }
}
