package com.branz.mmorpg.combat.health;

import java.util.Objects;

public record CombatHealthResolution(
        CombatHealthRuntime runtime, double appliedAmount, boolean lethalNow) {
    public CombatHealthResolution {
        Objects.requireNonNull(runtime, "runtime");
        if (!Double.isFinite(appliedAmount) || appliedAmount < 0) {
            throw new IllegalArgumentException("applied health amount must be non-negative");
        }
        if (lethalNow != (runtime.dead() && appliedAmount > 0)) {
            throw new IllegalArgumentException("lethal transition must agree with runtime");
        }
    }
}
