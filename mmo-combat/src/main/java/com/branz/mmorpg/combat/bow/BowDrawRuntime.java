package com.branz.mmorpg.combat.bow;

import java.util.Objects;

public record BowDrawRuntime(
        long startTick, long lastTick, BowDrawPhase phase, double strainDrainRemainder) {
    public BowDrawRuntime {
        Objects.requireNonNull(phase, "phase");
        if (startTick < 0
                || lastTick < startTick
                || !Double.isFinite(strainDrainRemainder)
                || strainDrainRemainder < 0
                || strainDrainRemainder >= 1) {
            throw new IllegalArgumentException("invalid bow draw runtime");
        }
    }
}
