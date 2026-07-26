package com.branz.mmorpg.api.crafting;

import com.branz.mmorpg.api.item.InventorySnapshot;

public record CraftFinalizeCommit(
        boolean applied,
        CraftJob job,
        InventorySnapshot inventoryBefore,
        InventorySnapshot inventoryAfter,
        java.util.Optional<ProfessionSnapshot> professionBefore,
        java.util.Optional<ProfessionSnapshot> professionAfter) {
}
