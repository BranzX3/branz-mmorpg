package com.branz.mmorpg.combat.input;

import java.util.Objects;

/** One synthetic client emission for deterministic latency/jitter acceptance simulations. */
public record LatencyInputEmission(
        long clientSequence,
        long emittedTick,
        int latencyTicks,
        int jitterTicks,
        SemanticInput input,
        DirectionSnapshot direction,
        String branchFamily,
        InputDeduplicationKey deduplicationKey) {
    public static final int MAXIMUM_EFFECTIVE_DELAY_TICKS = 40;

    public LatencyInputEmission {
        if (clientSequence < 1 || emittedTick < 0 || latencyTicks < 0) {
            throw new IllegalArgumentException("invalid latency emission sequence/tick");
        }
        long effectiveDelay = (long) latencyTicks + jitterTicks;
        if (effectiveDelay < 0 || effectiveDelay > MAXIMUM_EFFECTIVE_DELAY_TICKS) {
            throw new IllegalArgumentException(
                    "effective input delay must be between 0 and 40 ticks");
        }
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(branchFamily, "branchFamily");
        Objects.requireNonNull(deduplicationKey, "deduplicationKey");
        if (branchFamily.isBlank()) {
            throw new IllegalArgumentException("branchFamily must not be blank");
        }
    }

    public int effectiveDelayTicks() {
        return Math.addExact(latencyTicks, jitterTicks);
    }

    public long deliveryTick() {
        return Math.addExact(Math.addExact(emittedTick, effectiveDelayTicks()), 1);
    }
}
