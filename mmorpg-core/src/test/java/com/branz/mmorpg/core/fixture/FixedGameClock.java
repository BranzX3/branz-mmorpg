package com.branz.mmorpg.core.fixture;

import com.branz.mmorpg.api.runtime.GameClock;
import java.time.Duration;
import java.time.Instant;

/** Deterministic clock fixture; time only moves when a test moves it. */
public final class FixedGameClock implements GameClock {

    private Instant instant;
    private long nanos;

    public FixedGameClock(Instant start) {
        this.instant = start;
    }

    public static FixedGameClock at(String isoInstant) {
        return new FixedGameClock(Instant.parse(isoInstant));
    }

    public void advance(Duration amount) {
        instant = instant.plus(amount);
        nanos += amount.toNanos();
    }

    @Override
    public Instant now() {
        return instant;
    }

    @Override
    public long monotonicNanos() {
        return nanos;
    }
}
