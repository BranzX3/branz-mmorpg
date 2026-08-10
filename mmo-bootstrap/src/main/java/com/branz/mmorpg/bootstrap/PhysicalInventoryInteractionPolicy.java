package com.branz.mmorpg.bootstrap;

import java.util.Objects;
import org.bukkit.event.inventory.InventoryAction;

final class PhysicalInventoryInteractionPolicy {
    private PhysicalInventoryInteractionPolicy() {}

    static boolean supportsStorageAction(InventoryAction action) {
        Objects.requireNonNull(action, "action");
        return switch (action) {
            case PICKUP_ALL,
                    PICKUP_SOME,
                    PICKUP_HALF,
                    PICKUP_ONE,
                    PLACE_ALL,
                    PLACE_SOME,
                    PLACE_ONE,
                    SWAP_WITH_CURSOR -> true;
            default -> false;
        };
    }
}
