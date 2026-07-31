package com.branz.mmorpg.combat.guard;

import java.util.Objects;

public record CombatDefenseResolution(
        CombatDefenseOutcome outcome,
        GuardRuntime guardRuntime,
        double finalDamage,
        int staminaSpent,
        double stabilityPressure) {
    public CombatDefenseResolution {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(guardRuntime, "guardRuntime");
        if (!Double.isFinite(finalDamage)
                || finalDamage < 0
                || staminaSpent < 0
                || !Double.isFinite(stabilityPressure)
                || stabilityPressure < 0) {
            throw new IllegalArgumentException("invalid combat defense resolution");
        }
    }

    public boolean defended() {
        return outcome != CombatDefenseOutcome.HIT;
    }
}
