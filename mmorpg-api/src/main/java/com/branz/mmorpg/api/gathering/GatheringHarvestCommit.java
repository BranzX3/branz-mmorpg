package com.branz.mmorpg.api.gathering;

import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.lifeskill.LifeSkillSnapshot;

public record GatheringHarvestCommit(
        boolean applied,
        GatheringNodeInstance nodeBefore,
        GatheringNodeInstance nodeAfter,
        LifeSkillSnapshot skillBefore,
        LifeSkillSnapshot skillAfter,
        InventorySnapshot inventoryBefore,
        InventorySnapshot inventoryAfter) {
}
