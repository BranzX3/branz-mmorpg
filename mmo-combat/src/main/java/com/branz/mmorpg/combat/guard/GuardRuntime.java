package com.branz.mmorpg.combat.guard;

/** Immutable guard stability, hold and recovery state for one combatant. */
public record GuardRuntime(
        boolean active,
        long startedTick,
        double stability,
        long lastPressureTick,
        long brokenUntilTick,
        long lastTick,
        double recoveryRemainder) {
    public static final long NEVER = -1;

    public GuardRuntime {
        if (startedTick < NEVER
                || !Double.isFinite(stability)
                || stability < 0
                || lastPressureTick < NEVER
                || brokenUntilTick < NEVER
                || lastTick < 0
                || !Double.isFinite(recoveryRemainder)
                || recoveryRemainder < 0
                || recoveryRemainder >= 1) {
            throw new IllegalArgumentException("invalid guard runtime");
        }
        if (active && startedTick == NEVER) {
            throw new IllegalArgumentException("active guard requires a start tick");
        }
        if (brokenUntilTick != NEVER && active) {
            throw new IllegalArgumentException("broken guard cannot remain active");
        }
    }

    public static GuardRuntime initial(GuardProfile profile, long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative");
        }
        return new GuardRuntime(false, NEVER, profile.maximumStability(), NEVER, NEVER, tick, 0);
    }
}
