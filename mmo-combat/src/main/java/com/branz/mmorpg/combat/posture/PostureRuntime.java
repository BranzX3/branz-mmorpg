package com.branz.mmorpg.combat.posture;

public record PostureRuntime(
        double current,
        long lastDamageTick,
        long brokenUntilTick,
        long lastTick,
        double recoveryRemainder) {
    public static final long NEVER = -1;

    public PostureRuntime {
        if (!Double.isFinite(current)
                || current < 0
                || lastDamageTick < NEVER
                || brokenUntilTick < NEVER
                || lastTick < 0
                || !Double.isFinite(recoveryRemainder)
                || recoveryRemainder < 0
                || recoveryRemainder >= 1) {
            throw new IllegalArgumentException("invalid posture runtime");
        }
    }

    public static PostureRuntime initial(PostureProfile profile, long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative");
        }
        return new PostureRuntime(profile.maximum(), NEVER, NEVER, tick, 0);
    }
}
