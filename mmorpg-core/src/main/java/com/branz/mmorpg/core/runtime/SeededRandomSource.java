package com.branz.mmorpg.core.runtime;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.runtime.RandomSource;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Default {@link RandomSource}.
 *
 * <p>{@link #shared()} uses {@link ThreadLocalRandom} and is the production
 * choice: loot rolls happen on several threads and contention on one generator
 * is pure waste. {@link #seeded(long)} returns a single-threaded, reproducible
 * generator for tests and for any simulation that must replay identically.
 */
public final class SeededRandomSource implements RandomSource {

    private final SplittableRandom random;

    private SeededRandomSource(SplittableRandom random) {
        this.random = random;
    }

    /** Reproducible sequence. Not thread-safe by design. */
    public static SeededRandomSource seeded(long seed) {
        return new SeededRandomSource(new SplittableRandom(seed));
    }

    /** Thread-safe, non-reproducible source for production use. */
    public static RandomSource shared() {
        return new ThreadLocalRandomSource();
    }

    @Override
    public double nextDouble() {
        return random.nextDouble();
    }

    @Override
    public int nextInt(int bound) {
        requirePositive(bound);
        return random.nextInt(bound);
    }

    @Override
    public long nextLong() {
        return random.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return random.nextBoolean();
    }

    private static void requirePositive(int bound) {
        if (bound <= 0) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT, "bound must be positive: " + bound);
        }
    }

    private static final class ThreadLocalRandomSource implements RandomSource {

        @Override
        public double nextDouble() {
            return ThreadLocalRandom.current().nextDouble();
        }

        @Override
        public int nextInt(int bound) {
            requirePositive(bound);
            return ThreadLocalRandom.current().nextInt(bound);
        }

        @Override
        public long nextLong() {
            return ThreadLocalRandom.current().nextLong();
        }

        @Override
        public boolean nextBoolean() {
            return ThreadLocalRandom.current().nextBoolean();
        }
    }
}
