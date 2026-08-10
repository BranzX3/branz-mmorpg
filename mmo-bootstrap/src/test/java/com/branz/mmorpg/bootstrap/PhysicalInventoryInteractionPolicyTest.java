package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

class PhysicalInventoryInteractionPolicyTest {
    @Test
    void normalCursorPickupPlaceAndSwapAreSupported() {
        assertTrue(
                PhysicalInventoryInteractionPolicy.supportsStorageAction(
                        InventoryAction.PICKUP_ALL));
        assertTrue(
                PhysicalInventoryInteractionPolicy.supportsStorageAction(
                        InventoryAction.PLACE_ALL));
        assertTrue(
                PhysicalInventoryInteractionPolicy.supportsStorageAction(
                        InventoryAction.SWAP_WITH_CURSOR));
    }

    @Test
    void unownedInventoryMutationShapesRemainBlocked() {
        assertFalse(
                PhysicalInventoryInteractionPolicy.supportsStorageAction(
                        InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertFalse(
                PhysicalInventoryInteractionPolicy.supportsStorageAction(
                        InventoryAction.HOTBAR_SWAP));
        assertFalse(
                PhysicalInventoryInteractionPolicy.supportsStorageAction(
                        InventoryAction.DROP_ALL_SLOT));
        assertFalse(
                PhysicalInventoryInteractionPolicy.supportsStorageAction(
                        InventoryAction.COLLECT_TO_CURSOR));
    }
}
