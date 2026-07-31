package com.branz.mmorpg.combat.poise;

public record PoiseRuntime(
        double accumulated, long lastDamageTick, long lastTick, double decayRemainder) {
    public static final long NEVER = -1;

    public PoiseRuntime {
        if (!Double.isFinite(accumulated)
                || accumulated < 0
                || lastDamageTick < NEVER
                || lastTick < 0
                || !Double.isFinite(decayRemainder)
                || decayRemainder < 0
                || decayRemainder >= 1) {
            throw new IllegalArgumentException("invalid poise runtime");
        }
    }

    public static PoiseRuntime initial(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative");
        }
        return new PoiseRuntime(0, NEVER, tick, 0);
    }
}
