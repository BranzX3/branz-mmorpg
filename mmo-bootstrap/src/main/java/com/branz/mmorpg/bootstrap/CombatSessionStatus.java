package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.combat.action.ActionPhase;
import com.branz.mmorpg.combat.bow.BowDrawPhase;
import com.branz.mmorpg.combat.cc.CcSeverity;
import com.branz.mmorpg.combat.dodge.DodgeLoad;
import com.branz.mmorpg.combat.dodge.DodgePhase;
import com.branz.mmorpg.combat.guard.GuardPhase;
import com.branz.mmorpg.combat.state.EngagementState;
import com.branz.mmorpg.combat.state.WeaponState;
import java.util.Objects;
import java.util.Optional;

record CombatSessionStatus(
        EngagementState engagementState,
        int engagementExitTicksRemaining,
        WeaponState weaponState,
        Optional<ActionPhase> actionPhase,
        Optional<BowDrawPhase> bowDrawPhase,
        int bowRecoveryTicksRemaining,
        int activeProjectiles,
        boolean bowAmmoCommitPending,
        long bowAmmoQuantity,
        DodgeLoad dodgeLoad,
        Optional<DodgePhase> dodgePhase,
        GuardPhase guardPhase,
        double guardStability,
        Optional<CcSeverity> crowdControl,
        int crowdControlTicksRemaining,
        double health,
        double maximumHealth,
        boolean dead,
        int stamina,
        int reservedStamina,
        Optional<String> lastResolution) {
    CombatSessionStatus {
        Objects.requireNonNull(engagementState, "engagementState");
        Objects.requireNonNull(weaponState, "weaponState");
        Objects.requireNonNull(actionPhase, "actionPhase");
        Objects.requireNonNull(bowDrawPhase, "bowDrawPhase");
        Objects.requireNonNull(dodgeLoad, "dodgeLoad");
        Objects.requireNonNull(dodgePhase, "dodgePhase");
        Objects.requireNonNull(guardPhase, "guardPhase");
        Objects.requireNonNull(crowdControl, "crowdControl");
        Objects.requireNonNull(lastResolution, "lastResolution");
        if (engagementExitTicksRemaining < 0
                || bowRecoveryTicksRemaining < 0
                || activeProjectiles < 0
                || bowAmmoQuantity < 0
                || !Double.isFinite(guardStability)
                || guardStability < 0
                || crowdControlTicksRemaining < 0
                || !Double.isFinite(health)
                || health < 0
                || !Double.isFinite(maximumHealth)
                || maximumHealth <= 0
                || health > maximumHealth
                || dead != (health == 0)
                || stamina < 0
                || reservedStamina < 0) {
            throw new IllegalArgumentException("combat resources must not be negative");
        }
    }
}
