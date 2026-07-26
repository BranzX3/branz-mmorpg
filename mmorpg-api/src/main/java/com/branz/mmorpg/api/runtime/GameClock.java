package com.branz.mmorpg.api.runtime;

import java.time.Instant;

/**
 * Time source for core logic.
 *
 * <p>Domain code never calls {@code System.currentTimeMillis()} or
 * {@code Instant.now()} directly: cooldowns, decay windows, and anti-farm rules
 * must be testable at an arbitrary instant, and reproducible for the same
 * inputs.
 */
public interface GameClock {

    /** Wall-clock instant, for persistence and display. */
    Instant now();

    /**
     * Monotonically non-decreasing nanosecond reading, for measuring durations.
     * Unrelated to {@link #now()} and meaningless as an absolute time.
     */
    long monotonicNanos();

    default long epochMilli() {
        return now().toEpochMilli();
    }
}
