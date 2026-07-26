package com.branz.mmorpg.api.runtime;

/**
 * Randomness for loot rolls, critical hits, and gathering yields.
 *
 * <p>Injected rather than reached for statically so a test can pin a roll and
 * assert an exact outcome, as required by the determinism rule in
 * CORE_MMO_SPECIFICATION §3.
 */
public interface RandomSource {

    /** Uniform value in {@code [0, 1)}. */
    double nextDouble();

    /** Uniform value in {@code [0, bound)}. */
    int nextInt(int bound);

    long nextLong();

    boolean nextBoolean();

    /** Convenience roll: true with probability {@code chance}, clamped to [0, 1]. */
    default boolean roll(double chance) {
        if (chance <= 0.0) {
            return false;
        }
        if (chance >= 1.0) {
            return true;
        }
        return nextDouble() < chance;
    }
}
