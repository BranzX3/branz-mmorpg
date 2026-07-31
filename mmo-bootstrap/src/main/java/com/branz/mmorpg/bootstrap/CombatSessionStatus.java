package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.combat.action.ActionPhase;
import com.branz.mmorpg.combat.state.WeaponState;
import java.util.Objects;
import java.util.Optional;

record CombatSessionStatus(
        WeaponState weaponState,
        Optional<ActionPhase> actionPhase,
        int stamina,
        int reservedStamina) {
    CombatSessionStatus {
        Objects.requireNonNull(weaponState, "weaponState");
        Objects.requireNonNull(actionPhase, "actionPhase");
        if (stamina < 0 || reservedStamina < 0) {
            throw new IllegalArgumentException("combat resources must not be negative");
        }
    }
}
