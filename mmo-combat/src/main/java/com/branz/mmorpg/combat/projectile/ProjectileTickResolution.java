package com.branz.mmorpg.combat.projectile;

import com.branz.mmorpg.combat.hitbox.CombatVector;
import java.util.List;
import java.util.Objects;

public record ProjectileTickResolution(
        ProjectileRuntime runtime,
        CombatVector pathStart,
        CombatVector pathEnd,
        List<ProjectileHit> hits) {
    public ProjectileTickResolution {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(pathStart, "pathStart");
        Objects.requireNonNull(pathEnd, "pathEnd");
        hits = List.copyOf(Objects.requireNonNull(hits, "hits"));
    }
}
