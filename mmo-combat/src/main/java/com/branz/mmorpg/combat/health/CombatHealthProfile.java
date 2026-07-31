package com.branz.mmorpg.combat.health;

/** Authoritative health and open-world recovery tuning for a combatant archetype. */
public record CombatHealthProfile(
        double maximum,
        int openWorldRecoveryDelayTicks,
        double openWorldRecoveryRatioPerSecond,
        double openWorldRecoveryCapRatio,
        double respawnRatio) {
    public CombatHealthProfile {
        if (!Double.isFinite(maximum)
                || maximum <= 0
                || openWorldRecoveryDelayTicks < 0
                || !unitRatio(openWorldRecoveryRatioPerSecond)
                || !unitRatio(openWorldRecoveryCapRatio)
                || !Double.isFinite(respawnRatio)
                || respawnRatio <= 0
                || respawnRatio > 1) {
            throw new IllegalArgumentException("invalid combat health profile");
        }
    }

    public static CombatHealthProfile trainingPlayer() {
        return new CombatHealthProfile(1000, 400, 0.005, 0.80, 1.0);
    }

    public static CombatHealthProfile trainingEnemy() {
        return new CombatHealthProfile(1000, 400, 0, 0, 1.0);
    }

    private static boolean unitRatio(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }
}
