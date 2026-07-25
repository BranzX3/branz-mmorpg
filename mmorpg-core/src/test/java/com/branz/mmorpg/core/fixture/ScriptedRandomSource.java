package com.branz.mmorpg.core.fixture;

import com.branz.mmorpg.api.runtime.RandomSource;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Random fixture that returns a scripted sequence of doubles, so a test can pin
 * "this roll crits, the next one does not" without depending on a seed.
 *
 * <p>Running out of scripted values is a test bug, not a fallback: it throws.
 */
public final class ScriptedRandomSource implements RandomSource {

    private final Deque<Double> doubles = new ArrayDeque<>();

    public static ScriptedRandomSource of(double... values) {
        ScriptedRandomSource source = new ScriptedRandomSource();
        for (double value : values) {
            source.doubles.add(value);
        }
        return source;
    }

    @Override
    public double nextDouble() {
        Double value = doubles.poll();
        if (value == null) {
            throw new IllegalStateException("ScriptedRandomSource exhausted");
        }
        return value;
    }

    @Override
    public int nextInt(int bound) {
        return (int) (nextDouble() * bound);
    }

    @Override
    public long nextLong() {
        return (long) nextDouble();
    }

    @Override
    public boolean nextBoolean() {
        return nextDouble() < 0.5;
    }
}
