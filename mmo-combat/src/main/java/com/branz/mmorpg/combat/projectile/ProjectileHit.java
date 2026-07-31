package com.branz.mmorpg.combat.projectile;

import com.branz.mmorpg.combat.hitbox.CombatVector;
import java.util.Objects;
import java.util.UUID;

public record ProjectileHit(
        UUID entityId, double contactFraction, CombatVector contactPoint, boolean weakPoint) {
    public ProjectileHit {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(contactPoint, "contactPoint");
        if (!Double.isFinite(contactFraction) || contactFraction < 0 || contactFraction > 1) {
            throw new IllegalArgumentException("projectile contact fraction is invalid");
        }
    }
}
