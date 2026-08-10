package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.projection.ObservedProjection;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import java.util.Objects;

record ResolvedPhysicalItem(
        int slot,
        ObservedProjection projection,
        ItemLocationRecord record,
        ItemDefinition definition) {
    ResolvedPhysicalItem {
        if (slot < 0 || slot >= ChronicleService.HOTBAR_SLOT) {
            throw new IllegalArgumentException("physical gameplay slot must be hotbar 1-8");
        }
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(definition, "definition");
        if (projection.slot() != slot
                || !projection.valueId().equals(record.itemId().value())
                || !projection.definitionId().equals(record.definitionId())
                || !record.definitionId().equals(definition.id())) {
            throw new IllegalArgumentException("resolved physical item identities do not match");
        }
    }
}
