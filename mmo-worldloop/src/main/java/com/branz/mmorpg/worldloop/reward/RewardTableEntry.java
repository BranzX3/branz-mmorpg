package com.branz.mmorpg.worldloop.reward;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;

public record RewardTableEntry(
        DefinitionId itemDefinitionId, long weight, long minimumQuantity, long maximumQuantity) {
    public RewardTableEntry {
        Objects.requireNonNull(itemDefinitionId, "itemDefinitionId");
        if (weight < 1 || minimumQuantity < 1 || maximumQuantity < minimumQuantity) {
            throw new IllegalArgumentException("invalid reward table entry bounds");
        }
    }
}
