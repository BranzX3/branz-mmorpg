package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.projection.ExpectedProjection;
import com.branz.mmorpg.items.projection.ObservedProjection;
import com.branz.mmorpg.items.projection.ProjectionTokenSigner;
import com.branz.mmorpg.items.projection.ProjectionValueType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper PDC representation of a signed reference to an authoritative MMO value. */
final class BukkitItemProjectionCodec {
    private static final byte MARKER_VERSION = 1;

    private final ProjectionTokenSigner signer;
    private final NamespacedKey markerKey;
    private final NamespacedKey valueIdKey;
    private final NamespacedKey definitionIdKey;
    private final NamespacedKey valueTypeKey;
    private final NamespacedKey quantityKey;
    private final NamespacedKey authorityVersionKey;
    private final NamespacedKey displayRevisionKey;
    private final NamespacedKey contentVersionKey;
    private final NamespacedKey provenanceKey;
    private final NamespacedKey signatureKey;

    BukkitItemProjectionCodec(JavaPlugin plugin, ProjectionTokenSigner signer) {
        Objects.requireNonNull(plugin, "plugin");
        this.signer = Objects.requireNonNull(signer, "signer");
        markerKey = new NamespacedKey(plugin, "projection_version");
        valueIdKey = new NamespacedKey(plugin, "projection_value_id");
        definitionIdKey = new NamespacedKey(plugin, "projection_definition_id");
        valueTypeKey = new NamespacedKey(plugin, "projection_value_type");
        quantityKey = new NamespacedKey(plugin, "projection_quantity");
        authorityVersionKey = new NamespacedKey(plugin, "projection_authority_version");
        displayRevisionKey = new NamespacedKey(plugin, "projection_display_revision");
        contentVersionKey = new NamespacedKey(plugin, "projection_content_version");
        provenanceKey = new NamespacedKey(plugin, "projection_test_provenance");
        signatureKey = new NamespacedKey(plugin, "projection_signature");
    }

    ItemStack render(ExpectedProjection projection, ItemDefinition definition) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(definition, "definition");
        if (!projection.definitionId().equals(definition.id())) {
            throw new IllegalArgumentException(
                    "projection definition does not match item definition");
        }

        ItemStack item = new ItemStack(fallbackMaterial(definition));
        item.setAmount(Math.min(projection.quantity(), item.getMaxStackSize()));
        item.editMeta(
                meta -> {
                    meta.displayName(
                            Component.text(definition.id().value(), NamedTextColor.LIGHT_PURPLE));
                    meta.lore(
                            List.of(
                                    Component.text("Signed MMO projection", NamedTextColor.GRAY),
                                    Component.text(
                                            projection.valueType()
                                                    + " "
                                                    + projection
                                                            .valueId()
                                                            .toString()
                                                            .substring(0, 8),
                                            NamedTextColor.DARK_GRAY),
                                    Component.text(
                                            projection.testProvenance().isPresent()
                                                    ? "TEST ONLY — removed on logout"
                                                    : "Database-authoritative value",
                                            projection.testProvenance().isPresent()
                                                    ? NamedTextColor.RED
                                                    : NamedTextColor.DARK_GREEN)));
                    write(meta.getPersistentDataContainer(), projection);
                });
        return item;
    }

    private static Material fallbackMaterial(ItemDefinition definition) {
        if (definition.consumableProfile().isPresent()) {
            return switch (definition.consumableProfile().orElseThrow().category()) {
                case BODY_TONIC -> Material.POTION;
                case ELEMENTAL_WARD -> Material.SPLASH_POTION;
                case WEAPON_COATING -> Material.INK_SAC;
                case UTILITY_PREPARATION -> Material.FERMENTED_SPIDER_EYE;
                case MEAL -> Material.COOKED_BEEF;
            };
        }
        if (definition.id().value().startsWith("ammo.")) {
            return Material.ARROW;
        }
        if (definition.quiverProfile().isPresent()) {
            return Material.LEATHER;
        }
        if (definition.shieldProfile().isPresent()) {
            return Material.SHIELD;
        }
        return definition
                .weaponProfile()
                .map(
                        profile ->
                                switch (profile.family()) {
                                    case "BOW" -> Material.BOW;
                                    case "CROSSBOW" -> Material.CROSSBOW;
                                    case "SWORD", "SWORD_SHIELD" -> Material.IRON_SWORD;
                                    case "GREATSWORD" -> Material.IRON_AXE;
                                    case "STAFF" -> Material.BLAZE_ROD;
                                    default -> Material.BARRIER;
                                })
                .orElse(Material.BARRIER);
    }

    Optional<ObservedProjection> decode(ItemStack item, int slot) {
        if (!hasProjectionMarker(item)) {
            return Optional.empty();
        }
        try {
            PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
            UUID valueId = UUID.fromString(required(data, valueIdKey, PersistentDataType.STRING));
            DefinitionId definitionId =
                    DefinitionId.of(required(data, definitionIdKey, PersistentDataType.STRING));
            ProjectionValueType valueType =
                    ProjectionValueType.valueOf(
                            required(data, valueTypeKey, PersistentDataType.STRING));
            int quantity = required(data, quantityKey, PersistentDataType.INTEGER);
            long authorityVersion = required(data, authorityVersionKey, PersistentDataType.LONG);
            long displayRevision = required(data, displayRevisionKey, PersistentDataType.LONG);
            String contentVersion = required(data, contentVersionKey, PersistentDataType.STRING);
            Optional<String> provenance =
                    Optional.ofNullable(data.get(provenanceKey, PersistentDataType.STRING));
            byte[] signature = required(data, signatureKey, PersistentDataType.BYTE_ARRAY);
            ObservedProjection unsigned =
                    new ObservedProjection(
                            slot,
                            valueId,
                            definitionId,
                            valueType,
                            quantity,
                            authorityVersion,
                            displayRevision,
                            contentVersion,
                            provenance,
                            false);
            return Optional.of(
                    new ObservedProjection(
                            unsigned.slot(),
                            unsigned.valueId(),
                            unsigned.definitionId(),
                            unsigned.valueType(),
                            unsigned.quantity(),
                            unsigned.authorityVersion(),
                            unsigned.displayRevision(),
                            unsigned.contentVersion(),
                            unsigned.testProvenance(),
                            signer.verify(unsigned, signature)));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return Optional.empty();
        }
    }

    boolean hasProjectionMarker(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        Byte marker =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(markerKey, PersistentDataType.BYTE);
        return marker != null && marker == MARKER_VERSION;
    }

    boolean isTestProjection(ItemStack item) {
        return hasProjectionMarker(item)
                && item.getItemMeta()
                                .getPersistentDataContainer()
                                .get(provenanceKey, PersistentDataType.STRING)
                        != null;
    }

    private void write(PersistentDataContainer data, ExpectedProjection projection) {
        data.set(markerKey, PersistentDataType.BYTE, MARKER_VERSION);
        data.set(valueIdKey, PersistentDataType.STRING, projection.valueId().toString());
        data.set(definitionIdKey, PersistentDataType.STRING, projection.definitionId().value());
        data.set(valueTypeKey, PersistentDataType.STRING, projection.valueType().name());
        data.set(quantityKey, PersistentDataType.INTEGER, projection.quantity());
        data.set(authorityVersionKey, PersistentDataType.LONG, projection.authorityVersion());
        data.set(displayRevisionKey, PersistentDataType.LONG, projection.displayRevision());
        data.set(contentVersionKey, PersistentDataType.STRING, projection.contentVersion());
        projection
                .testProvenance()
                .ifPresent(
                        provenance ->
                                data.set(provenanceKey, PersistentDataType.STRING, provenance));
        data.set(signatureKey, PersistentDataType.BYTE_ARRAY, signer.sign(projection));
    }

    private static <P, C> C required(
            PersistentDataContainer data, NamespacedKey key, PersistentDataType<P, C> type) {
        C value = data.get(key, type);
        if (value == null) {
            throw new IllegalStateException("projection field is missing: " + key);
        }
        return value;
    }
}
