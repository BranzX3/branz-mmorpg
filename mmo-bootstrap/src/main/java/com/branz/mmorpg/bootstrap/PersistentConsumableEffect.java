package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.items.consumable.ConsumableCategory;
import java.util.Objects;

record PersistentConsumableEffect(
        DefinitionId definitionId, ConsumableCategory category, int remainingTicks, boolean rare) {
    PersistentConsumableEffect {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(category, "category");
        if (remainingTicks < 1) {
            throw new IllegalArgumentException("remainingTicks must be positive");
        }
    }
}
