package com.branz.mmorpg.items.definition;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.definition.ContentDefinition;
import com.branz.mmorpg.content.schema.DefinitionType;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Immutable runtime item-definition view compiled from the active content snapshot. */
public final class ItemEngine {
    private final Map<DefinitionId, ItemDefinition> definitions;

    private ItemEngine(Map<DefinitionId, ItemDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public static Result<ItemEngine, ItemEngineErrorCode> compile(ContentSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        LinkedHashMap<DefinitionId, ItemDefinition> compiled = new LinkedHashMap<>();
        for (ContentDefinition source : snapshot.definitions().byType(DefinitionType.ITEM)) {
            Result<ItemDefinition, ItemEngineErrorCode> result = compileDefinition(source);
            if (result instanceof Result.Failure<ItemDefinition, ItemEngineErrorCode> failure) {
                return Result.failure(
                        failure.error(), source.id().value() + ": " + failure.detail());
            }
            ItemDefinition definition =
                    ((Result.Success<ItemDefinition, ItemEngineErrorCode>) result).value();
            compiled.put(definition.id(), definition);
        }
        return Result.success(new ItemEngine(compiled));
    }

    public Optional<ItemDefinition> find(DefinitionId id) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(id, "id")));
    }

    public Collection<ItemDefinition> all() {
        return definitions.values();
    }

    private static Result<ItemDefinition, ItemEngineErrorCode> compileDefinition(
            ContentDefinition source) {
        JsonNode body = source.body();
        Result<DefinitionId, ?> parsedAsset = DefinitionId.parse(body.path("asset_id").asText(""));
        if (parsedAsset instanceof Result.Failure<?, ?> failure) {
            return Result.failure(
                    ItemEngineErrorCode.ITEM_ASSET_ID_INVALID,
                    "asset_id is not a stable dotted identifier: " + failure.detail());
        }
        DefinitionId assetId = ((Result.Success<DefinitionId, ?>) parsedAsset).value();

        ItemClass itemClass;
        try {
            itemClass = ItemClass.valueOf(body.path("item_class").asText(""));
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    ItemEngineErrorCode.ITEM_CLASS_INVALID,
                    "item_class must be UNIQUE_DURABLE or STACKABLE_LOT");
        }

        OptionalInt durability = OptionalInt.empty();
        JsonNode durabilityNode = body.get("base_max_durability");
        if (durabilityNode != null && !durabilityNode.isNull()) {
            if (!durabilityNode.canConvertToInt() || durabilityNode.intValue() < 1) {
                return Result.failure(
                        ItemEngineErrorCode.ITEM_DURABILITY_INVALID,
                        "base_max_durability must be a positive integer");
            }
            durability = OptionalInt.of(durabilityNode.intValue());
        }

        boolean cosmetic = source.id().value().startsWith("cosmetic.");
        if (cosmetic && durability.isPresent()) {
            return Result.failure(
                    ItemEngineErrorCode.ITEM_DURABILITY_INVALID,
                    "cosmetic definitions cannot declare durability");
        }
        Optional<WeaponCombatProfile> weaponProfile = Optional.empty();
        JsonNode weaponNode = body.get("weapon_profile");
        if (weaponNode != null && !weaponNode.isNull()) {
            String family = weaponNode.path("family").asText("");
            JsonNode power = weaponNode.get("power");
            if (family.isBlank()
                    || power == null
                    || !power.isNumber()
                    || !Double.isFinite(power.doubleValue())
                    || power.doubleValue() <= 0) {
                return Result.failure(
                        ItemEngineErrorCode.ITEM_WEAPON_PROFILE_INVALID,
                        "weapon_profile requires non-blank family and positive power");
            }
            weaponProfile = Optional.of(new WeaponCombatProfile(family, power.doubleValue()));
        }
        return Result.success(
                new ItemDefinition(
                        source.id(), assetId, itemClass, durability, cosmetic, weaponProfile));
    }
}
