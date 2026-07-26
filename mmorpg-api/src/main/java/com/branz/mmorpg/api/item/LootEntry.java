package com.branz.mmorpg.api.item;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Objects;
import java.util.Set;

public record LootEntry(
        String entryId,
        ContentId itemId,
        double weight,
        boolean guaranteed,
        long minimumQuantity,
        long maximumQuantity,
        Set<String> requiredConditions,
        int pityAfter,
        long perRollCap) {

    public LootEntry {
        entryId = Objects.requireNonNull(entryId, "entryId").trim();
        Objects.requireNonNull(itemId, "itemId");
        requiredConditions = Set.copyOf(requiredConditions);
        if (entryId.isEmpty() || !Double.isFinite(weight) || weight < 0
                || (!guaranteed && weight <= 0) || minimumQuantity < 1
                || maximumQuantity < minimumQuantity || pityAfter < 0
                || perRollCap < maximumQuantity) {
            throw new IllegalArgumentException("invalid loot entry");
        }
    }
}
