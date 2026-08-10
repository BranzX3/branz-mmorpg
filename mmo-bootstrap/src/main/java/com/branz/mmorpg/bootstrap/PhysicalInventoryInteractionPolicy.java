package com.branz.mmorpg.bootstrap;

import java.util.Objects;
import java.util.Set;

final class PhysicalInventoryInteractionPolicy {
    private static final Set<String> SUPPORTED_STORAGE_ACTIONS =
            Set.of(
                    "PICKUP_ALL",
                    "PICKUP_SOME",
                    "PICKUP_HALF",
                    "PICKUP_ONE",
                    "PLACE_ALL",
                    "PLACE_SOME",
                    "PLACE_ONE",
                    "SWAP_WITH_CURSOR");

    private PhysicalInventoryInteractionPolicy() {}

    static boolean supportsStorageAction(String actionName) {
        return SUPPORTED_STORAGE_ACTIONS.contains(
                Objects.requireNonNull(actionName, "actionName"));
    }
}
