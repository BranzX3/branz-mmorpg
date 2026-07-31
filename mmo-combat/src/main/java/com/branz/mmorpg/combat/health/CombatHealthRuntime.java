package com.branz.mmorpg.combat.health;

public record CombatHealthRuntime(double current, long lastDamageTick, long lastTick) {
    public static final long NEVER = -1;

    public CombatHealthRuntime {
        if (!Double.isFinite(current)
                || current < 0
                || lastDamageTick < NEVER
                || lastTick < 0
                || lastDamageTick > lastTick) {
            throw new IllegalArgumentException("invalid combat health runtime");
        }
    }

    public static CombatHealthRuntime full(CombatHealthProfile profile, long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative");
        }
        return new CombatHealthRuntime(profile.maximum(), NEVER, tick);
    }

    public boolean dead() {
        return current == 0;
    }
}
