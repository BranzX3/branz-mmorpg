package com.branz.mmorpg.combat.input;

import com.branz.mmorpg.combat.state.WeaponState;
import com.branz.mmorpg.combat.weapon.SelectedSlotKind;
import java.util.Objects;

/** Decides whether incoming hostile damage should select and draw the equipped weapon. */
public final class HostileAutoDrawPolicy {
    private HostileAutoDrawPolicy() {}

    public static boolean shouldBeginDraw(
            WeaponState weapon,
            SelectedSlotKind equippedCombatSlot,
            boolean combatReady,
            boolean dead) {
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(equippedCombatSlot, "equippedCombatSlot");
        return !dead
                && combatReady
                && equippedCombatSlot == SelectedSlotKind.COMBAT_WEAPON
                && (weapon == WeaponState.SHEATHED || weapon == WeaponState.SHEATHING);
    }
}
