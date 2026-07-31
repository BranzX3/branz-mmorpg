package com.branz.mmorpg.combat.guard;

import com.branz.mmorpg.combat.dodge.DodgeEngine;
import com.branz.mmorpg.combat.dodge.DodgeRuntime;
import java.util.Objects;
import java.util.Optional;

/** Applies the canonical same-tick order: dodge, perfect guard, guard, hit. */
public final class CombatDefenseResolver {
    private final DodgeEngine dodges;
    private final GuardEngine guards;

    public CombatDefenseResolver(DodgeEngine dodges, GuardEngine guards) {
        this.dodges = Objects.requireNonNull(dodges, "dodges");
        this.guards = Objects.requireNonNull(guards, "guards");
    }

    public CombatDefenseResolution resolve(
            Optional<DodgeRuntime> dodge,
            GuardRuntime guard,
            long hitTick,
            boolean dodgeable,
            GuardHitRequest request) {
        Objects.requireNonNull(dodge, "dodge");
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(request, "request");
        if (dodge.isPresent() && dodges.avoids(dodge.orElseThrow(), hitTick, dodgeable)) {
            return new CombatDefenseResolution(CombatDefenseOutcome.DODGED, guard, 0, 0, 0);
        }
        GuardResolution guarded = guards.resolve(guard, hitTick, request);
        CombatDefenseOutcome outcome =
                switch (guarded.outcome()) {
                    case PERFECT_GUARD -> CombatDefenseOutcome.PERFECT_GUARD;
                    case GUARDED -> CombatDefenseOutcome.GUARDED;
                    case GUARD_BREAK -> CombatDefenseOutcome.GUARD_BREAK;
                    case UNGUARDED, OUTSIDE_CONE, EXHAUSTED -> CombatDefenseOutcome.HIT;
                };
        return new CombatDefenseResolution(
                outcome,
                guarded.runtime(),
                guarded.finalDamage(),
                guarded.staminaSpent(),
                guarded.stabilityPressure());
    }
}
