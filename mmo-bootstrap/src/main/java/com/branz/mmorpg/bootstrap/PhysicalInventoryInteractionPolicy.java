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
        String checked = Objects.requireNonNull(actionName, "actionName");
        boolean supported = SUPPORTED_STORAGE_ACTIONS.contains(checked);
        System.out.println(
                "PHYSICAL_AUTHORITY_C12_DIAG_POLICY_SERVER action="
                        + checked
                        + " supported="
                        + supported);
        return supported;
    }
}
