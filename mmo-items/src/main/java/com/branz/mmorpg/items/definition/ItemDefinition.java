package com.branz.mmorpg.items.definition;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record ItemDefinition(
        DefinitionId id,
        DefinitionId assetId,
        ItemClass itemClass,
        OptionalInt baseMaxDurability,
        boolean cosmetic,
        Optional<WeaponCombatProfile> weaponProfile) {
    public ItemDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(itemClass, "itemClass");
        Objects.requireNonNull(baseMaxDurability, "baseMaxDurability");
        Objects.requireNonNull(weaponProfile, "weaponProfile");
        if (baseMaxDurability.isPresent() && baseMaxDurability.getAsInt() < 1) {
            throw new IllegalArgumentException("baseMaxDurability must be positive");
        }
        if (cosmetic && baseMaxDurability.isPresent()) {
            throw new IllegalArgumentException("cosmetic definitions cannot have durability");
        }
        if (cosmetic && weaponProfile.isPresent()) {
            throw new IllegalArgumentException("cosmetic definitions cannot have weapon stats");
        }
    }

    public ItemDefinition(
            DefinitionId id,
            DefinitionId assetId,
            ItemClass itemClass,
            OptionalInt baseMaxDurability,
            boolean cosmetic) {
        this(id, assetId, itemClass, baseMaxDurability, cosmetic, Optional.empty());
    }
}
