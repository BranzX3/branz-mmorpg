package com.branz.mmorpg.combat.weapon;

import java.util.Objects;

public record SelectedHotbarSlot(int slot, SelectedSlotKind kind) {
    public SelectedHotbarSlot {
        if (slot < 0 || slot > 8) {
            throw new IllegalArgumentException("hotbar slot must be between 0 and 8");
        }
        Objects.requireNonNull(kind, "kind");
        if (kind == SelectedSlotKind.COMBAT_WEAPON && slot == 8) {
            throw new IllegalArgumentException("Chronicle slot cannot be a combat weapon");
        }
        if (kind == SelectedSlotKind.CHRONICLE && slot != 8) {
            throw new IllegalArgumentException("Chronicle must occupy hotbar slot 8");
        }
    }

    public static SelectedHotbarSlot combatWeapon(int slot) {
        return new SelectedHotbarSlot(slot, SelectedSlotKind.COMBAT_WEAPON);
    }
}
