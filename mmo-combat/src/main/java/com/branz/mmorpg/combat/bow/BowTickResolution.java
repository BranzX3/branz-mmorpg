package com.branz.mmorpg.combat.bow;

import java.util.Objects;

public record BowTickResolution(
        BowDrawRuntime runtime, int staminaSpent, boolean loweredForExhaustion) {
    public BowTickResolution {
        Objects.requireNonNull(runtime, "runtime");
        if (staminaSpent < 0
                || loweredForExhaustion != (runtime.phase() == BowDrawPhase.CANCELLED)) {
            throw new IllegalArgumentException("invalid bow tick resolution");
        }
    }
}
