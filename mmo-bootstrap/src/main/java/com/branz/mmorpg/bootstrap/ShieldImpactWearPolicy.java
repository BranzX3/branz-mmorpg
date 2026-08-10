package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.combat.guard.CombatDefenseOutcome;
import java.util.Objects;

final class ShieldImpactWearPolicy {
    private ShieldImpactWearPolicy() {}

    static boolean consumesDurability(CombatDefenseOutcome outcome) {
        return switch (Objects.requireNonNull(outcome, "outcome")) {
            case PERFECT_GUARD, GUARDED, GUARD_BREAK -> true;
            case DODGED, HIT -> false;
        };
    }
}
