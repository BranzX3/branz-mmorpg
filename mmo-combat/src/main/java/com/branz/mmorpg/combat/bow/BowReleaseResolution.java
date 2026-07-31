package com.branz.mmorpg.combat.bow;

import java.util.Objects;
import java.util.Optional;

public record BowReleaseResolution(
        BowDrawRuntime runtime,
        BowReleaseOutcome outcome,
        int staminaSpent,
        Optional<BowShotCharge> shot) {
    public BowReleaseResolution {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(shot, "shot");
        if (staminaSpent < 0 || (outcome == BowReleaseOutcome.FIRED) != shot.isPresent()) {
            throw new IllegalArgumentException("invalid bow release resolution");
        }
    }
}
