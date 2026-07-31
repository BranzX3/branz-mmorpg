package com.branz.mmorpg.items.definition;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.definition.ContentDefinition;
import com.branz.mmorpg.content.schema.DefinitionType;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
            try {
                Optional<BowWeaponProfile> bowProfile =
                        compileBowProfile(family, weaponNode.get("bow"));
                Optional<CrossbowWeaponProfile> crossbowProfile =
                        compileCrossbowProfile(family, weaponNode.get("crossbow"));
                OffhandPolicy offhandPolicy =
                        OffhandPolicy.valueOf(weaponNode.path("offhand_policy").asText("ANY"));
                Optional<GuardCombatProfile> guardProfile =
                        compileGuardProfile(weaponNode.get("guard"));
                weaponProfile =
                        Optional.of(
                                new WeaponCombatProfile(
                                        family,
                                        power.doubleValue(),
                                        bowProfile,
                                        crossbowProfile,
                                        offhandPolicy,
                                        guardProfile));
            } catch (IllegalArgumentException exception) {
                return Result.failure(
                        ItemEngineErrorCode.ITEM_WEAPON_PROFILE_INVALID, exception.getMessage());
            }
        }
        Optional<AmmoProfile> ammoProfile;
        try {
            ammoProfile = compileAmmoProfile(source.id(), itemClass, body.get("ammo_profile"));
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    ItemEngineErrorCode.ITEM_AMMO_PROFILE_INVALID, exception.getMessage());
        }
        Optional<QuiverProfile> quiverProfile;
        try {
            quiverProfile = compileQuiverProfile(itemClass, durability, body.get("quiver_profile"));
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    ItemEngineErrorCode.ITEM_QUIVER_PROFILE_INVALID, exception.getMessage());
        }
        Optional<CatalystProfile> catalystProfile;
        try {
            catalystProfile =
                    compileCatalystProfile(itemClass, durability, body.get("catalyst_profile"));
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    ItemEngineErrorCode.ITEM_CATALYST_PROFILE_INVALID, exception.getMessage());
        }
        Optional<ShieldProfile> shieldProfile;
        try {
            shieldProfile = compileShieldProfile(itemClass, durability, body.get("shield_profile"));
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    ItemEngineErrorCode.ITEM_SHIELD_PROFILE_INVALID, exception.getMessage());
        }
        try {
            return Result.success(
                    new ItemDefinition(
                            source.id(),
                            assetId,
                            itemClass,
                            durability,
                            cosmetic,
                            weaponProfile,
                            ammoProfile,
                            quiverProfile,
                            catalystProfile,
                            shieldProfile));
        } catch (IllegalArgumentException exception) {
            return Result.failure(ItemEngineErrorCode.ITEM_CLASS_INVALID, exception.getMessage());
        }
    }

    private static Optional<AmmoProfile> compileAmmoProfile(
            DefinitionId id, ItemClass itemClass, JsonNode ammoNode) {
        boolean ammoIdentity = id.value().startsWith("ammo.");
        boolean declared = ammoNode != null && !ammoNode.isNull();
        if (ammoIdentity != declared) {
            throw new IllegalArgumentException(
                    "ammo.* definitions require ammo_profile and other identities forbid it");
        }
        if (!declared) {
            return Optional.empty();
        }
        if (itemClass != ItemClass.STACKABLE_LOT || !ammoNode.isObject()) {
            throw new IllegalArgumentException("ammo_profile requires STACKABLE_LOT object");
        }
        try {
            return Optional.of(
                    new AmmoProfile(AmmoFamily.valueOf(ammoNode.path("family").asText(""))));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("ammo_profile.family must be ARROW or BOLT");
        }
    }

    private static Optional<QuiverProfile> compileQuiverProfile(
            ItemClass itemClass, OptionalInt durability, JsonNode quiverNode) {
        if (quiverNode == null || quiverNode.isNull()) {
            return Optional.empty();
        }
        if (itemClass != ItemClass.UNIQUE_DURABLE
                || durability.isPresent()
                || !quiverNode.isObject()) {
            throw new IllegalArgumentException(
                    "quiver_profile requires a non-durability UNIQUE_DURABLE item");
        }
        JsonNode familiesNode = quiverNode.get("supported_ammo_families");
        if (familiesNode == null || !familiesNode.isArray()) {
            throw new IllegalArgumentException("supported_ammo_families must be an array");
        }
        EnumSet<AmmoFamily> families = EnumSet.noneOf(AmmoFamily.class);
        try {
            familiesNode.forEach(node -> families.add(AmmoFamily.valueOf(node.asText(""))));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "supported_ammo_families values must be ARROW or BOLT");
        }
        return Optional.of(
                new QuiverProfile(
                        requiredInteger(quiverNode, "capacity"),
                        families,
                        requiredInteger(quiverNode, "prepared_ammo_category_count"),
                        requiredInteger(quiverNode, "ammo_switch_handling_ticks")));
    }

    private static Optional<CatalystProfile> compileCatalystProfile(
            ItemClass itemClass, OptionalInt durability, JsonNode catalystNode) {
        if (catalystNode == null || catalystNode.isNull()) {
            return Optional.empty();
        }
        if (itemClass != ItemClass.UNIQUE_DURABLE
                || durability.isEmpty()
                || !catalystNode.isObject()) {
            throw new IllegalArgumentException(
                    "catalyst_profile requires a durable UNIQUE_DURABLE item");
        }
        JsonNode tagsNode = catalystNode.get("tags");
        if (tagsNode == null || !tagsNode.isArray() || tagsNode.isEmpty()) {
            throw new IllegalArgumentException("catalyst_profile.tags must contain entries");
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (JsonNode tag : tagsNode) {
            if (!tag.isTextual() || tag.textValue().isBlank()) {
                throw new IllegalArgumentException(
                        "catalyst_profile.tags entries must be non-blank text");
            }
            tags.add(tag.textValue());
        }
        return Optional.of(
                new CatalystProfile(
                        tags,
                        requiredNumber(catalystNode, "channel_stability"),
                        requiredInteger(catalystNode, "durability_cost_per_commit")));
    }

    private static Optional<ShieldProfile> compileShieldProfile(
            ItemClass itemClass, OptionalInt durability, JsonNode shieldNode) {
        if (shieldNode == null || shieldNode.isNull()) {
            return Optional.empty();
        }
        if (itemClass != ItemClass.UNIQUE_DURABLE
                || durability.isEmpty()
                || !shieldNode.isObject()) {
            throw new IllegalArgumentException(
                    "shield_profile requires a durable UNIQUE_DURABLE item");
        }
        Optional<GuardCombatProfile> guard = compileGuardProfile(shieldNode.get("guard"));
        if (guard.isEmpty()) {
            throw new IllegalArgumentException("shield_profile requires guard fields");
        }
        return Optional.of(new ShieldProfile(guard.orElseThrow()));
    }

    private static Optional<GuardCombatProfile> compileGuardProfile(JsonNode guardNode) {
        if (guardNode == null || guardNode.isNull()) {
            return Optional.empty();
        }
        if (!guardNode.isObject()) {
            throw new IllegalArgumentException("guard profile must be an object");
        }
        return Optional.of(
                new GuardCombatProfile(
                        requiredNumber(guardNode, "cone_degrees"),
                        requiredNumber(guardNode, "physical_block_ratio"),
                        requiredInteger(guardNode, "perfect_window_ticks"),
                        requiredNumber(guardNode, "maximum_stability"),
                        requiredInteger(guardNode, "recovery_delay_ticks"),
                        requiredNumber(guardNode, "inactive_recovery_per_second"),
                        requiredNumber(guardNode, "active_recovery_per_second"),
                        requiredInteger(guardNode, "break_ticks"),
                        requiredNumber(guardNode, "stability_after_break")));
    }

    private static Optional<BowWeaponProfile> compileBowProfile(String family, JsonNode bowNode) {
        boolean declared = bowNode != null && !bowNode.isNull();
        if (!family.equals("BOW")) {
            if (declared) {
                throw new IllegalArgumentException(
                        "weapon_profile.bow is valid only for BOW family");
            }
            return Optional.empty();
        }
        if (!declared || !bowNode.isObject()) {
            throw new IllegalArgumentException("BOW weapon_profile requires bow handling fields");
        }
        try {
            return Optional.of(
                    new BowWeaponProfile(
                            requiredInteger(bowNode, "minimum_draw_ticks"),
                            requiredInteger(bowNode, "full_draw_ticks"),
                            requiredInteger(bowNode, "free_full_draw_hold_ticks"),
                            requiredNumber(bowNode, "strain_stamina_per_second"),
                            requiredNumber(bowNode, "minimum_velocity_multiplier"),
                            requiredNumber(bowNode, "minimum_posture_multiplier"),
                            requiredNumber(bowNode, "maximum_penetration_percentage")));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "invalid weapon_profile.bow: " + exception.getMessage(), exception);
        }
    }

    private static Optional<CrossbowWeaponProfile> compileCrossbowProfile(
            String family, JsonNode crossbowNode) {
        boolean declared = crossbowNode != null && !crossbowNode.isNull();
        if (!family.equals("CROSSBOW")) {
            if (declared) {
                throw new IllegalArgumentException(
                        "weapon_profile.crossbow is valid only for CROSSBOW family");
            }
            return Optional.empty();
        }
        if (!declared || !crossbowNode.isObject()) {
            throw new IllegalArgumentException(
                    "CROSSBOW weapon_profile requires checkpoint timing fields");
        }
        try {
            return Optional.of(
                    new CrossbowWeaponProfile(
                            requiredInteger(crossbowNode, "bolt_placement_ticks"),
                            requiredInteger(crossbowNode, "locking_ticks")));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "invalid weapon_profile.crossbow: " + exception.getMessage(), exception);
        }
    }

    private static int requiredInteger(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return node.intValue();
    }

    private static double requiredNumber(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isNumber() || !Double.isFinite(node.doubleValue())) {
            throw new IllegalArgumentException(field + " must be a finite number");
        }
        return node.doubleValue();
    }
}
