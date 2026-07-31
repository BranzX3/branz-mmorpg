package com.branz.mmorpg.combat.projectile;

import com.branz.mmorpg.combat.hitbox.TargetCollider;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/** One server-tick collision query. Block contact is a normalized swept-path fraction. */
public record ProjectileTickQuery(
        ProjectileRuntime runtime,
        List<TargetCollider> candidates,
        OptionalDouble blockContactFraction) {
    public ProjectileTickQuery {
        Objects.requireNonNull(runtime, "runtime");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        Objects.requireNonNull(blockContactFraction, "blockContactFraction");
        if (blockContactFraction.isPresent()
                && (!Double.isFinite(blockContactFraction.getAsDouble())
                        || blockContactFraction.getAsDouble() < 0
                        || blockContactFraction.getAsDouble() > 1)) {
            throw new IllegalArgumentException(
                    "block contact fraction must be between zero and one");
        }
    }
}
