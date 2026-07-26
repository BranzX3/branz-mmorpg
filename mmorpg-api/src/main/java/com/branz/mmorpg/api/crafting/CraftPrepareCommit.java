package com.branz.mmorpg.api.crafting;

import com.branz.mmorpg.api.item.InventorySnapshot;

public record CraftPrepareCommit(
        boolean created,
        CraftJob job,
        InventorySnapshot inventoryBefore,
        InventorySnapshot inventoryAfter) {
}
