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
        Optional<WeaponCombatProfile> weaponProfile,
        Optional<AmmoProfile> ammoProfile,
        Optional<QuiverProfile> quiverProfile,
        Optional<CatalystProfile> catalystProfile,
        Optional<ShieldProfile> shieldProfile) {
    public ItemDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(itemClass, "itemClass");
        Objects.requireNonNull(baseMaxDurability, "baseMaxDurability");
        Objects.requireNonNull(weaponProfile, "weaponProfile");
        Objects.requireNonNull(ammoProfile, "ammoProfile");
        Objects.requireNonNull(quiverProfile, "quiverProfile");
        Objects.requireNonNull(catalystProfile, "catalystProfile");
        Objects.requireNonNull(shieldProfile, "shieldProfile");
        if (baseMaxDurability.isPresent() && baseMaxDurability.getAsInt() < 1) {
            throw new IllegalArgumentException("baseMaxDurability must be positive");
        }
        if (cosmetic && baseMaxDurability.isPresent()) {
            throw new IllegalArgumentException("cosmetic definitions cannot have durability");
        }
        if (cosmetic
                && (weaponProfile.isPresent()
                        || ammoProfile.isPresent()
                        || quiverProfile.isPresent()
                        || catalystProfile.isPresent()
                        || shieldProfile.isPresent())) {
            throw new IllegalArgumentException(
                    "cosmetic definitions cannot have gameplay profiles");
        }
        if (java.util.stream.Stream.of(weaponProfile, ammoProfile, quiverProfile, shieldProfile)
                        .filter(Optional::isPresent)
                        .count()
                > 1) {
            throw new IllegalArgumentException("item runtime profiles are mutually exclusive");
        }
        if (ammoProfile.isPresent() && itemClass != ItemClass.STACKABLE_LOT) {
            throw new IllegalArgumentException("ammo profiles require STACKABLE_LOT");
        }
        if (quiverProfile.isPresent()
                && (itemClass != ItemClass.UNIQUE_DURABLE || baseMaxDurability.isPresent())) {
            throw new IllegalArgumentException(
                    "Quiver profiles require a non-durability UNIQUE_DURABLE item");
        }
        if (catalystProfile.isPresent()
                && (itemClass != ItemClass.UNIQUE_DURABLE || baseMaxDurability.isEmpty())) {
            throw new IllegalArgumentException(
                    "Catalyst profiles require a durable UNIQUE_DURABLE item");
        }
        if (catalystProfile.isPresent() && (ammoProfile.isPresent() || quiverProfile.isPresent())) {
            throw new IllegalArgumentException(
                    "Catalyst profiles cannot share ammo or Quiver runtime profiles");
        }
        if (shieldProfile.isPresent()
                && (itemClass != ItemClass.UNIQUE_DURABLE || baseMaxDurability.isEmpty())) {
            throw new IllegalArgumentException(
                    "Shield profiles require a durable UNIQUE_DURABLE item");
        }
    }

    public ItemDefinition(
            DefinitionId id,
            DefinitionId assetId,
            ItemClass itemClass,
            OptionalInt baseMaxDurability,
            boolean cosmetic,
            Optional<WeaponCombatProfile> weaponProfile,
            Optional<AmmoProfile> ammoProfile,
            Optional<QuiverProfile> quiverProfile,
            Optional<CatalystProfile> catalystProfile) {
        this(
                id,
                assetId,
                itemClass,
                baseMaxDurability,
                cosmetic,
                weaponProfile,
                ammoProfile,
                quiverProfile,
                catalystProfile,
                Optional.empty());
    }

    public ItemDefinition(
            DefinitionId id,
            DefinitionId assetId,
            ItemClass itemClass,
            OptionalInt baseMaxDurability,
            boolean cosmetic,
            Optional<WeaponCombatProfile> weaponProfile,
            Optional<AmmoProfile> ammoProfile,
            Optional<QuiverProfile> quiverProfile) {
        this(
                id,
                assetId,
                itemClass,
                baseMaxDurability,
                cosmetic,
                weaponProfile,
                ammoProfile,
                quiverProfile,
                Optional.empty(),
                Optional.empty());
    }

    public ItemDefinition(
            DefinitionId id,
            DefinitionId assetId,
            ItemClass itemClass,
            OptionalInt baseMaxDurability,
            boolean cosmetic,
            Optional<WeaponCombatProfile> weaponProfile) {
        this(
                id,
                assetId,
                itemClass,
                baseMaxDurability,
                cosmetic,
                weaponProfile,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public ItemDefinition(
            DefinitionId id,
            DefinitionId assetId,
            ItemClass itemClass,
            OptionalInt baseMaxDurability,
            boolean cosmetic) {
        this(
                id,
                assetId,
                itemClass,
                baseMaxDurability,
                cosmetic,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
