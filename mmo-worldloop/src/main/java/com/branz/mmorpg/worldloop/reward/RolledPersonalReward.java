package com.branz.mmorpg.worldloop.reward;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.LotId;
import java.util.Objects;

public record RolledPersonalReward(DefinitionId itemDefinitionId, long quantity, LotId lotId) {
    public RolledPersonalReward {
        Objects.requireNonNull(itemDefinitionId, "itemDefinitionId");
        Objects.requireNonNull(lotId, "lotId");
        if (quantity < 1) {
            throw new IllegalArgumentException("reward quantity must be positive");
        }
    }
}
