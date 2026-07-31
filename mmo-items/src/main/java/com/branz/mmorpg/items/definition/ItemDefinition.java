package com.branz.mmorpg.items.definition;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;
import java.util.OptionalInt;

public record ItemDefinition(
        DefinitionId id,
        DefinitionId assetId,
        ItemClass itemClass,
        OptionalInt baseMaxDurability,
        boolean cosmetic) {
    public ItemDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(itemClass, "itemClass");
        Objects.requireNonNull(baseMaxDurability, "baseMaxDurability");
        if (baseMaxDurability.isPresent() && baseMaxDurability.getAsInt() < 1) {
            throw new IllegalArgumentException("baseMaxDurability must be positive");
        }
        if (cosmetic && baseMaxDurability.isPresent()) {
            throw new IllegalArgumentException("cosmetic definitions cannot have durability");
        }
    }
}
