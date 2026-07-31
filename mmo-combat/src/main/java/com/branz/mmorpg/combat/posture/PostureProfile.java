package com.branz.mmorpg.combat.posture;

/** Normal-enemy posture defaults for the local combat training target. */
public record PostureProfile(
        double maximum, int recoveryDelayTicks, double recoveryPerSecond, int breakTicks) {
    public PostureProfile {
        if (!Double.isFinite(maximum)
                || maximum <= 0
                || recoveryDelayTicks < 0
                || !Double.isFinite(recoveryPerSecond)
                || recoveryPerSecond < 0
                || breakTicks < 1) {
            throw new IllegalArgumentException("invalid posture profile");
        }
    }

    public static PostureProfile trainingNormal() {
        return new PostureProfile(100, 60, 25, 60);
    }
}
