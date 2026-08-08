package com.branz.mmorpg.combat.input;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.combat.state.WeaponState;
import com.branz.mmorpg.combat.weapon.SelectedSlotKind;
import org.junit.jupiter.api.Test;

class HostileAutoDrawPolicyTest {
    @Test
    void sheathedReadyCombatWeaponDrawsOnIncomingHostility() {
        assertTrue(
                HostileAutoDrawPolicy.shouldBeginDraw(
                        WeaponState.SHEATHED, SelectedSlotKind.COMBAT_WEAPON, true, false));
    }

    @Test
    void sheathingWeaponCanReverseBackTowardCombatReadiness() {
        assertTrue(
                HostileAutoDrawPolicy.shouldBeginDraw(
                        WeaponState.SHEATHING, SelectedSlotKind.COMBAT_WEAPON, true, false));
    }

    @Test
    void alreadyDrawingReadyOrDisabledDoesNotRestartDraw() {
        assertFalse(
                HostileAutoDrawPolicy.shouldBeginDraw(
                        WeaponState.DRAWING, SelectedSlotKind.COMBAT_WEAPON, true, false));
        assertFalse(
                HostileAutoDrawPolicy.shouldBeginDraw(
                        WeaponState.READY, SelectedSlotKind.COMBAT_WEAPON, true, false));
        assertFalse(
                HostileAutoDrawPolicy.shouldBeginDraw(
                        WeaponState.DISABLED, SelectedSlotKind.COMBAT_WEAPON, true, false));
    }

    @Test
    void missingInvalidOrDeadWeaponStateNeverAutoDraws() {
        assertFalse(
                HostileAutoDrawPolicy.shouldBeginDraw(
                        WeaponState.SHEATHED, SelectedSlotKind.TOOL_OR_BLOCK, true, false));
        assertFalse(
                HostileAutoDrawPolicy.shouldBeginDraw(
                        WeaponState.SHEATHED, SelectedSlotKind.COMBAT_WEAPON, false, false));
        assertFalse(
                HostileAutoDrawPolicy.shouldBeginDraw(
                        WeaponState.SHEATHED, SelectedSlotKind.COMBAT_WEAPON, true, true));
    }
}
