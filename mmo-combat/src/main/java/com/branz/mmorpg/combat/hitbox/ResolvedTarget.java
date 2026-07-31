package com.branz.mmorpg.combat.hitbox;

import java.util.Objects;
import java.util.UUID;

public record ResolvedTarget(
        UUID entityId, boolean weakPoint, double distance, double angleDegrees) {
    public ResolvedTarget {
        Objects.requireNonNull(entityId, "entityId");
        if (!Double.isFinite(distance)
                || distance < 0
                || !Double.isFinite(angleDegrees)
                || angleDegrees < 0
                || angleDegrees > 180) {
            throw new IllegalArgumentException("invalid resolved target metrics");
        }
    }
}
