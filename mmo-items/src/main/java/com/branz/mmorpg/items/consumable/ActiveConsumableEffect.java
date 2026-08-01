package com.branz.mmorpg.items.consumable;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;

public record ActiveConsumableEffect(
        DefinitionId effectId, ConsumableCategory category, long expiresTick, boolean rare) {
    public ActiveConsumableEffect {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(category, "category");
        if (expiresTick < 1) {
            throw new IllegalArgumentException("expiresTick must be positive");
        }
    }

    public boolean activeAt(long currentTick) {
        return currentTick < expiresTick;
    }
}
