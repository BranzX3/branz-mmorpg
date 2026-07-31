package com.branz.mmorpg.combat.guard;

import com.branz.mmorpg.combat.hitbox.CombatVector;
import java.util.Objects;

public record GuardHitRequest(
        double incomingDamage,
        double guardPressure,
        boolean guardable,
        boolean perfectGuardable,
        CombatVector defenderFacing,
        CombatVector directionToAttacker,
        int availableStamina) {
    public GuardHitRequest {
        if (!Double.isFinite(incomingDamage)
                || incomingDamage < 0
                || !Double.isFinite(guardPressure)
                || guardPressure < 0
                || availableStamina < 0) {
            throw new IllegalArgumentException("invalid guarded hit request");
        }
        Objects.requireNonNull(defenderFacing, "defenderFacing").normalizedHorizontal();
        Objects.requireNonNull(directionToAttacker, "directionToAttacker").normalizedHorizontal();
    }
}
