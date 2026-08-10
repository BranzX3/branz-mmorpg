package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhysicalInventoryInteractionPolicyTest {
    @Test
    void normalCursorPickupPlaceAndSwapAreSupported() {
        assertTrue(PhysicalInventoryInteractionPolicy.supportsStorageAction("PICKUP_ALL"));
        assertTrue(PhysicalInventoryInteractionPolicy.supportsStorageAction("PICKUP_HALF"));
        assertTrue(PhysicalInventoryInteractionPolicy.supportsStorageAction("PLACE_ALL"));
        assertTrue(PhysicalInventoryInteractionPolicy.supportsStorageAction("PLACE_ONE"));
        assertTrue(PhysicalInventoryInteractionPolicy.supportsStorageAction("SWAP_WITH_CURSOR"));
    }

    @Test
    void unownedInventoryMutationShapesRemainBlocked() {
        assertFalse(
                PhysicalInventoryInteractionPolicy.supportsStorageAction(
                        "MOVE_TO_OTHER_INVENTORY"));
        assertFalse(PhysicalInventoryInteractionPolicy.supportsStorageAction("HOTBAR_SWAP"));
        assertFalse(
                PhysicalInventoryInteractionPolicy.supportsStorageAction("HOTBAR_MOVE_AND_READD"));
        assertFalse(PhysicalInventoryInteractionPolicy.supportsStorageAction("DROP_ALL_SLOT"));
        assertFalse(PhysicalInventoryInteractionPolicy.supportsStorageAction("COLLECT_TO_CURSOR"));
    }
}
