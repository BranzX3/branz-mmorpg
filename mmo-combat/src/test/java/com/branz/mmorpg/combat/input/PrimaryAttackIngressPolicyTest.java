package com.branz.mmorpg.combat.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.branz.mmorpg.combat.state.WeaponState;
import com.branz.mmorpg.combat.weapon.SelectedSlotKind;
import org.junit.jupiter.api.Test;

class PrimaryAttackIngressPolicyTest {
    @Test
    void readyAndDrawingRoutePrimaryWithoutEngagementPrerequisite() {
        assertEquals(
                new PrimaryAttackIngressDecision(true, false, true),
                PrimaryAttackIngressPolicy.decide(
                        WeaponState.READY, SelectedSlotKind.COMBAT_WEAPON, false));
        assertEquals(
                new PrimaryAttackIngressDecision(true, false, true),
                PrimaryAttackIngressPolicy.decide(
                        WeaponState.DRAWING, SelectedSlotKind.COMBAT_WEAPON, false));
    }

    @Test
    void sheathedCombatWeaponBeginsDrawAndRoutesBufferedOpener() {
        assertEquals(
                new PrimaryAttackIngressDecision(true, true, true),
                PrimaryAttackIngressPolicy.decide(
                        WeaponState.SHEATHED, SelectedSlotKind.COMBAT_WEAPON, false));
    }

    @Test
    void sheathedNonCombatSlotsRemainVanillaOwned() {
        for (SelectedSlotKind kind :
                new SelectedSlotKind[] {
                    SelectedSlotKind.CONSUMABLE,
                    SelectedSlotKind.TOOL_OR_BLOCK,
                    SelectedSlotKind.EMPTY,
                    SelectedSlotKind.CHRONICLE
                }) {
            assertEquals(
                    new PrimaryAttackIngressDecision(false, false, false),
                    PrimaryAttackIngressPolicy.decide(WeaponState.SHEATHED, kind, false));
        }
    }

    @Test
    void activeReadyTimelineRoutesIntoServerBufferWithoutLeakingVanillaDamage() {
        assertEquals(
                new PrimaryAttackIngressDecision(true, false, true),
                PrimaryAttackIngressPolicy.decide(
                        WeaponState.READY, SelectedSlotKind.COMBAT_WEAPON, true));
        assertEquals(
                new PrimaryAttackIngressDecision(true, false, true),
                PrimaryAttackIngressPolicy.decide(
                        WeaponState.DRAWING, SelectedSlotKind.COMBAT_WEAPON, true));
    }

    @Test
    void lockedCombatStatesNeverLeakVanillaDamage() {
        assertEquals(
                new PrimaryAttackIngressDecision(true, false, false),
                PrimaryAttackIngressPolicy.decide(
                        WeaponState.SHEATHING, SelectedSlotKind.COMBAT_WEAPON, true));
        assertEquals(
                new PrimaryAttackIngressDecision(true, false, false),
                PrimaryAttackIngressPolicy.decide(
                        WeaponState.SHEATHING, SelectedSlotKind.COMBAT_WEAPON, false));
        assertEquals(
                new PrimaryAttackIngressDecision(true, false, false),
                PrimaryAttackIngressPolicy.decide(
                        WeaponState.DISABLED, SelectedSlotKind.COMBAT_WEAPON, false));
    }
}
