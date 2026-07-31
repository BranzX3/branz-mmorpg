package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.combat.action.ActionPhase;
import com.branz.mmorpg.combat.state.EngagementState;
import com.branz.mmorpg.combat.state.WeaponState;
import java.util.Objects;
import java.util.Optional;

record CombatSessionStatus(
        EngagementState engagementState,
        int engagementExitTicksRemaining,
        WeaponState weaponState,
        Optional<ActionPhase> actionPhase,
        int stamina,
        int reservedStamina,
        Optional<String> lastResolution) {
    CombatSessionStatus {
        Objects.requireNonNull(engagementState, "engagementState");
        Objects.requireNonNull(weaponState, "weaponState");
        Objects.requireNonNull(actionPhase, "actionPhase");
        Objects.requireNonNull(lastResolution, "lastResolution");
        if (engagementExitTicksRemaining < 0 || stamina < 0 || reservedStamina < 0) {
            throw new IllegalArgumentException("combat resources must not be negative");
        }
    }
}
