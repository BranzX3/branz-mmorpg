package com.branz.mmorpg.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.core.fixture.FixedGameClock;
import com.branz.mmorpg.core.fixture.ScriptedRandomSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guards the determinism rule in CORE_MMO_SPECIFICATION §3. */
class DeterminismTest {

    @Test
    void seededSourceReplaysTheSameSequence() {
        List<Double> first = rolls(SeededRandomSource.seeded(20260725L));
        List<Double> second = rolls(SeededRandomSource.seeded(20260725L));
        assertEquals(first, second);
    }

    @Test
    void rollClampsInsteadOfSamplingAtTheBoundaries() {
        ScriptedRandomSource source = ScriptedRandomSource.of(0.99);
        assertFalse(source.roll(0.0), "an impossible roll must not consume a value");
        assertTrue(source.roll(1.0), "a certain roll must not consume a value");
        assertFalse(source.roll(0.5), "0.99 is above the 0.5 threshold");
    }

    @Test
    void fixedClockMovesOnlyWhenAdvanced() {
        FixedGameClock clock = FixedGameClock.at("2026-07-25T12:00:00Z");
        Instant start = clock.now();
        long startNanos = clock.monotonicNanos();

        assertEquals(start, clock.now());

        clock.advance(Duration.ofSeconds(90));
        assertEquals(start.plusSeconds(90), clock.now());
        assertEquals(startNanos + Duration.ofSeconds(90).toNanos(), clock.monotonicNanos());
    }

    @Test
    void systemClockMonotonicReadingNeverGoesBackwards() {
        SystemGameClock clock = new SystemGameClock();
        long first = clock.monotonicNanos();
        long second = clock.monotonicNanos();
        assertTrue(second >= first);
    }

    private static List<Double> rolls(SeededRandomSource source) {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            values.add(source.nextDouble());
        }
        return values;
    }
}
