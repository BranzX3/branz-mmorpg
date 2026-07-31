package com.branz.mmorpg.combat.projectile;

import com.branz.mmorpg.combat.hitbox.CombatVector;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable projectile state advanced exactly once per authoritative server tick. */
public record ProjectileRuntime(
        ProjectileIdentity identity,
        ProjectileProfile profile,
        CombatVector position,
        CombatVector velocity,
        int ageTicks,
        int remainingPierces,
        Set<UUID> hitTargets,
        ProjectileStatus status) {
    public ProjectileRuntime {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(velocity, "velocity");
        hitTargets = Set.copyOf(Objects.requireNonNull(hitTargets, "hitTargets"));
        Objects.requireNonNull(status, "status");
        if (ageTicks < 0
                || ageTicks > profile.lifetimeTicks()
                || remainingPierces < 0
                || remainingPierces > profile.pierceCount()) {
            throw new IllegalArgumentException("invalid projectile runtime");
        }
    }

    public static ProjectileRuntime launch(
            ProjectileIdentity identity,
            ProjectileProfile profile,
            CombatVector origin,
            CombatVector direction,
            double speedMultiplier) {
        if (!Double.isFinite(speedMultiplier) || speedMultiplier <= 0 || speedMultiplier > 2) {
            throw new IllegalArgumentException("projectile speed multiplier is invalid");
        }
        return new ProjectileRuntime(
                identity,
                profile,
                origin,
                direction.normalized().multiply(profile.baseSpeed() * speedMultiplier),
                0,
                profile.pierceCount(),
                Set.of(),
                ProjectileStatus.FLYING);
    }
}
