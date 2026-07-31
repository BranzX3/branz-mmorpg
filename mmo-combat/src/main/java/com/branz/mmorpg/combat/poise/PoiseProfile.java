package com.branz.mmorpg.combat.poise;

public record PoiseProfile(
        double threshold, int accumulationWindowTicks, double decayRatioPerSecond) {
    public PoiseProfile {
        if (!Double.isFinite(threshold)
                || threshold <= 0
                || accumulationWindowTicks < 0
                || !Double.isFinite(decayRatioPerSecond)
                || decayRatioPerSecond < 0
                || decayRatioPerSecond > 1) {
            throw new IllegalArgumentException("invalid poise profile");
        }
    }

    public static PoiseProfile trainingPlayer() {
        return new PoiseProfile(30, 10, 0.30);
    }
}
