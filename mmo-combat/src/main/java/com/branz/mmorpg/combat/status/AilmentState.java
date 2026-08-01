package com.branz.mmorpg.combat.status;

/** One ailment projection at the last evaluated server tick. */
public record AilmentState(
        double buildup, long lastBuildupTick, long evaluatedTick, long activeUntilTick, int tier) {
    public AilmentState {
        if (!Double.isFinite(buildup)
                || buildup < 0
                || lastBuildupTick < 0
                || evaluatedTick < lastBuildupTick
                || activeUntilTick < 0
                || tier < 0
                || (tier == 0) != (activeUntilTick == 0)) {
            throw new IllegalArgumentException("invalid ailment state");
        }
    }

    public static AilmentState empty(long currentTick) {
        return new AilmentState(0, currentTick, currentTick, 0, 0);
    }

    public boolean activeAt(long currentTick) {
        return tier > 0 && currentTick < activeUntilTick;
    }
}
