package com.branz.mmorpg.combat.input;

import com.branz.mmorpg.combat.state.WeaponState;
import com.branz.mmorpg.combat.weapon.SelectedSlotKind;
import java.util.Objects;

/** Decides physical LMB ownership before Bukkit can apply vanilla damage. */
public final class PrimaryAttackIngressPolicy {
    private static final PrimaryAttackIngressDecision VANILLA =
            new PrimaryAttackIngressDecision(false, false, false);
    private static final PrimaryAttackIngressDecision OWNED_LOCKED =
            new PrimaryAttackIngressDecision(true, false, false);
    private static final PrimaryAttackIngressDecision ROUTE =
            new PrimaryAttackIngressDecision(true, false, true);
    private static final PrimaryAttackIngressDecision DRAW_AND_ROUTE =
            new PrimaryAttackIngressDecision(true, true, true);

    private PrimaryAttackIngressPolicy() {}

    public static PrimaryAttackIngressDecision decide(
            WeaponState weapon, SelectedSlotKind selectedSlot, boolean actionActive) {
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(selectedSlot, "selectedSlot");
        if (actionActive) {
            return weapon == WeaponState.READY || weapon == WeaponState.DRAWING
                    ? ROUTE
                    : OWNED_LOCKED;
        }
        return switch (weapon) {
            case READY, DRAWING -> ROUTE;
            case SHEATHED ->
                    selectedSlot == SelectedSlotKind.COMBAT_WEAPON ? DRAW_AND_ROUTE : VANILLA;
            case SHEATHING, DISABLED ->
                    selectedSlot == SelectedSlotKind.COMBAT_WEAPON ? OWNED_LOCKED : VANILLA;
        };
    }
}
