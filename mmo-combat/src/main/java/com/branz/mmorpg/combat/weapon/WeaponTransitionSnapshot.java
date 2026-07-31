package com.branz.mmorpg.combat.weapon;

import com.branz.mmorpg.combat.state.WeaponState;
import java.util.Objects;
import java.util.OptionalInt;

public record WeaponTransitionSnapshot(
        WeaponState state,
        OptionalInt activeWeaponSlot,
        OptionalInt desiredWeaponSlot,
        int ticksRemaining,
        long revision) {
    public WeaponTransitionSnapshot {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(activeWeaponSlot, "activeWeaponSlot");
        Objects.requireNonNull(desiredWeaponSlot, "desiredWeaponSlot");
        validateSlot(activeWeaponSlot);
        validateSlot(desiredWeaponSlot);
        if (ticksRemaining < 0 || revision < 0) {
            throw new IllegalArgumentException("ticks and revision must not be negative");
        }
        if ((state == WeaponState.SHEATHED || state == WeaponState.DISABLED)
                && activeWeaponSlot.isPresent()) {
            throw new IllegalArgumentException(state + " cannot retain an active weapon");
        }
        if (state == WeaponState.READY && activeWeaponSlot.isEmpty()) {
            throw new IllegalArgumentException("READY requires an active weapon");
        }
        if ((state == WeaponState.DRAWING || state == WeaponState.SHEATHING)
                && ticksRemaining == 0) {
            throw new IllegalArgumentException("transition state requires remaining ticks");
        }
        if (state == WeaponState.DRAWING && desiredWeaponSlot.isEmpty()) {
            throw new IllegalArgumentException("DRAWING requires a desired weapon");
        }
        if (state != WeaponState.DRAWING && state != WeaponState.SHEATHING && ticksRemaining != 0) {
            throw new IllegalArgumentException("stable state cannot retain transition ticks");
        }
    }

    public static WeaponTransitionSnapshot initial() {
        return new WeaponTransitionSnapshot(
                WeaponState.SHEATHED, OptionalInt.empty(), OptionalInt.empty(), 0, 0);
    }

    private static void validateSlot(OptionalInt slot) {
        if (slot.isPresent() && (slot.getAsInt() < 0 || slot.getAsInt() > 7)) {
            throw new IllegalArgumentException("combat weapon slot must be between 0 and 7");
        }
    }
}
