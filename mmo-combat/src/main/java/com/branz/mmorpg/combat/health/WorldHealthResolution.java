package com.branz.mmorpg.combat.health;

/** One deterministic damage application against an ordinary world entity's canonical health. */
public record WorldHealthResolution(
        double previous, double current, double maximum, double appliedAmount, boolean lethalNow) {
    public WorldHealthResolution {
        if (!Double.isFinite(previous)
                || !Double.isFinite(current)
                || !Double.isFinite(maximum)
                || !Double.isFinite(appliedAmount)
                || maximum <= 0
                || previous < 0
                || previous > maximum
                || current < 0
                || current > previous
                || appliedAmount < 0
                || Math.abs((previous - current) - appliedAmount) > 1.0e-9
                || lethalNow != (current <= 0)) {
            throw new IllegalArgumentException("invalid world health resolution");
        }
    }
}
