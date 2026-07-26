package com.branz.mmorpg.api.social;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record TradeOffer(Map<ContentId, Long> materials, Set<UUID> itemIds) {
    public TradeOffer {
        materials = Map.copyOf(materials);
        itemIds = Set.copyOf(itemIds);
        materials.forEach((id, quantity) -> {
            if (id == null || quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("invalid trade material quantity");
            }
        });
    }

    public static TradeOffer empty() { return new TradeOffer(Map.of(), Set.of()); }
}
