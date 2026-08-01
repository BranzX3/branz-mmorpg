package com.branz.mmorpg.magic.cast;

import java.util.Objects;

/** One deterministic channel tick. Effects are emitted only when {@code pulseEmitted} is true. */
public record ChannelPulseResolution(
        SpellCastRuntime runtime, boolean pulseEmitted, boolean endedForInsufficientMana) {
    public ChannelPulseResolution {
        Objects.requireNonNull(runtime, "runtime");
        if (pulseEmitted && endedForInsufficientMana) {
            throw new IllegalArgumentException("a rejected upkeep tick cannot emit an effect");
        }
    }
}
