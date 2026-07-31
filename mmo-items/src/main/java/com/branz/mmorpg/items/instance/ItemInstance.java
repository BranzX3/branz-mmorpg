package com.branz.mmorpg.items.instance;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Immutable persistent state for one unique/durable item UUID. */
public record ItemInstance(
        ItemId itemId,
        DefinitionId definitionId,
        long definitionRevision,
        CharacterId ownerCharacterId,
        ItemLocation location,
        Map<String, BigDecimal> qualityRolls,
        int enhancementLevel,
        Optional<String> enhancementPath,
        OptionalInt currentDurability,
        OptionalInt maxDurability,
        Optional<String> loadedAmmoState,
        Optional<String> cosmeticDyeState,
        Instant createdAt,
        long version) {
    public ItemInstance {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(definitionId, "definitionId");
        if (definitionRevision < 1) {
            throw new IllegalArgumentException("definitionRevision must be positive");
        }
        Objects.requireNonNull(ownerCharacterId, "ownerCharacterId");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(qualityRolls, "qualityRolls");
        LinkedHashMap<String, BigDecimal> copiedRolls = new LinkedHashMap<>();
        qualityRolls.forEach(
                (key, value) -> {
                    if (key == null || key.isBlank()) {
                        throw new IllegalArgumentException("quality-roll key must not be blank");
                    }
                    copiedRolls.put(key, Objects.requireNonNull(value, "quality-roll value"));
                });
        qualityRolls = Map.copyOf(copiedRolls);
        if (enhancementLevel < 0 || enhancementLevel > 10) {
            throw new IllegalArgumentException("enhancementLevel must be between 0 and 10");
        }
        enhancementPath = requireOptionalText(enhancementPath, "enhancementPath");
        Objects.requireNonNull(currentDurability, "currentDurability");
        Objects.requireNonNull(maxDurability, "maxDurability");
        if (currentDurability.isPresent() != maxDurability.isPresent()) {
            throw new IllegalArgumentException(
                    "current and maximum durability must both be present or absent");
        }
        if (maxDurability.isPresent()) {
            int maximum = maxDurability.getAsInt();
            int current = currentDurability.getAsInt();
            if (maximum < 1 || current < 0 || current > maximum) {
                throw new IllegalArgumentException(
                        "durability must satisfy 0 <= current <= maximum");
            }
        }
        loadedAmmoState = requireOptionalText(loadedAmmoState, "loadedAmmoState");
        cosmeticDyeState = requireOptionalText(cosmeticDyeState, "cosmeticDyeState");
        Objects.requireNonNull(createdAt, "createdAt");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }

    public ItemInstance relocated(ItemLocation destination, long expectedVersion) {
        Objects.requireNonNull(destination, "destination");
        if (version != expectedVersion) {
            throw new IllegalArgumentException("item version changed before relocation");
        }
        return new ItemInstance(
                itemId,
                definitionId,
                definitionRevision,
                ownerCharacterId,
                destination,
                qualityRolls,
                enhancementLevel,
                enhancementPath,
                currentDurability,
                maxDurability,
                loadedAmmoState,
                cosmeticDyeState,
                createdAt,
                version + 1);
    }

    private static Optional<String> requireOptionalText(Optional<String> value, String name) {
        Objects.requireNonNull(value, name);
        return value.map(
                text -> {
                    if (text.isBlank()) {
                        throw new IllegalArgumentException(name + " must not be blank");
                    }
                    return text;
                });
    }
}
