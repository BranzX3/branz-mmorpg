package com.branz.mmorpg.combat.hitbox;

import java.util.Objects;

public record ArcHitboxQuery(
        CombatVector origin,
        CombatVector forward,
        double range,
        double angleDegrees,
        double verticalMinimum,
        double verticalMaximum,
        int maximumTargets) {
    public ArcHitboxQuery {
        Objects.requireNonNull(origin, "origin");
        forward = Objects.requireNonNull(forward, "forward").normalizedHorizontal();
        if (!Double.isFinite(range)
                || range <= 0
                || !Double.isFinite(angleDegrees)
                || angleDegrees <= 0
                || angleDegrees > 360
                || !Double.isFinite(verticalMinimum)
                || !Double.isFinite(verticalMaximum)
                || verticalMaximum < verticalMinimum
                || maximumTargets < 1
                || maximumTargets > 8) {
            throw new IllegalArgumentException("invalid ARC hitbox query");
        }
    }
}
