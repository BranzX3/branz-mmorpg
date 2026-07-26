package com.branz.mmorpg.core.runtime;

import com.branz.mmorpg.api.runtime.GameClock;
import java.time.Instant;

/** Production {@link GameClock} backed by the system clock. */
public final class SystemGameClock implements GameClock {

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public long monotonicNanos() {
        return System.nanoTime();
    }
}
