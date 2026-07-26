package com.branz.mmorpg.api.item;

import com.branz.mmorpg.api.content.ContentId;

public record LootAward(ContentId itemId, long quantity, String entryId) {
    public LootAward {
        if (quantity < 1) throw new IllegalArgumentException("loot quantity must be positive");
    }
}
