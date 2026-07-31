package com.branz.mmorpg.combat.hitbox;

import java.util.Objects;
import java.util.UUID;

public record TargetCollider(
        UUID entityId,
        CombatVector feetPosition,
        double radius,
        double height,
        boolean eligible,
        boolean lineOfSight,
        boolean weakPoint) {
    public TargetCollider {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(feetPosition, "feetPosition");
        if (!Double.isFinite(radius) || radius < 0 || !Double.isFinite(height) || height <= 0) {
            throw new IllegalArgumentException("invalid target collider");
        }
    }
}
