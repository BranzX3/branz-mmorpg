package com.branz.mmorpg.combat.guard;

import java.util.Objects;

public record GuardResolution(
        GuardHitOutcome outcome,
        GuardRuntime runtime,
        double finalDamage,
        int staminaSpent,
        double stabilityPressure) {
    public GuardResolution {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(runtime, "runtime");
        if (!Double.isFinite(finalDamage)
                || finalDamage < 0
                || staminaSpent < 0
                || !Double.isFinite(stabilityPressure)
                || stabilityPressure < 0) {
            throw new IllegalArgumentException("invalid guard resolution");
        }
    }

    public boolean defended() {
        return outcome == GuardHitOutcome.GUARDED
                || outcome == GuardHitOutcome.PERFECT_GUARD
                || outcome == GuardHitOutcome.GUARD_BREAK;
    }
}
