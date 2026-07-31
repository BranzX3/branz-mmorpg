package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.combat.input.DirectionSnapshot;
import java.util.Objects;

final class AmmoCycleInputPolicy {
    private AmmoCycleInputPolicy() {}

    static boolean ownsScroll(boolean sneaking, DirectionSnapshot direction, String weaponFamily) {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(weaponFamily, "weaponFamily");
        return sneaking
                && direction == DirectionSnapshot.NEUTRAL
                && (weaponFamily.equals("BOW") || weaponFamily.equals("CROSSBOW"));
    }

    static int scrollDirection(int previousSlot, int newSlot) {
        if (previousSlot < 0 || previousSlot > 8 || newSlot < 0 || newSlot > 8) {
            throw new IllegalArgumentException("hotbar slots must be between zero and eight");
        }
        if (previousSlot == newSlot) {
            throw new IllegalArgumentException("scroll must change the proposed hotbar slot");
        }
        if (previousSlot == 8 && newSlot == 0) {
            return 1;
        }
        if (previousSlot == 0 && newSlot == 8) {
            return -1;
        }
        return Integer.compare(newSlot, previousSlot);
    }
}
