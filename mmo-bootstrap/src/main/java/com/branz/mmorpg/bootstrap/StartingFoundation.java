package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The V1 first-session choice is a starting kit, never a permanent class lock. */
enum StartingFoundation {
    GREATSWORD(
            "Greatsword",
            "Committed arcs, guard pressure, deliberate timing",
            List.of(item("weapon.training_greatsword", EquipmentSlot.MAIN_HAND)),
            Optional.empty()),
    SWORD_AND_SHIELD(
            "Sword + Shield",
            "Stable guard, forgiving defense, close-range control",
            List.of(
                    item("weapon.training_sword", EquipmentSlot.MAIN_HAND),
                    item("equipment.training_shield", EquipmentSlot.OFF_HAND)),
            Optional.empty()),
    BOW(
            "Bow",
            "Ranged draw timing with a prepared training quiver",
            List.of(
                    item("weapon.training_bow", EquipmentSlot.MAIN_HAND),
                    item("equipment.training_quiver", EquipmentSlot.QUIVER)),
            Optional.of(new StarterLot(DefinitionId.of("ammo.training_arrow"), 48))),
    STAFF_EMBER(
            "Staff / Ember",
            "Staff combat first; authored Ember knowledge follows through the tutorial",
            List.of(item("weapon.training_staff", EquipmentSlot.MAIN_HAND)),
            Optional.empty());

    private final String displayName;
    private final String description;
    private final List<StarterItem> items;
    private final Optional<StarterLot> lot;

    StartingFoundation(
            String displayName,
            String description,
            List<StarterItem> items,
            Optional<StarterLot> lot) {
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.description = Objects.requireNonNull(description, "description");
        this.items = List.copyOf(items);
        this.lot = Objects.requireNonNull(lot, "lot");
    }

    String displayName() {
        return displayName;
    }

    String description() {
        return description;
    }

    List<StarterItem> items() {
        return items;
    }

    Optional<StarterLot> lot() {
        return lot;
    }

    Optional<DefinitionId> quiverDefinitionId() {
        return items.stream()
                .filter(item -> item.slot() == EquipmentSlot.QUIVER)
                .map(StarterItem::definitionId)
                .findFirst();
    }

    Optional<String> availabilityFailure(ItemEngine itemEngine) {
        Objects.requireNonNull(itemEngine, "itemEngine");
        for (StarterItem item : items) {
            if (itemEngine.find(item.definitionId()).isEmpty()) {
                return Optional.of(
                        "Missing starter item definition " + item.definitionId().value());
            }
        }
        if (lot.isPresent() && itemEngine.find(lot.orElseThrow().definitionId()).isEmpty()) {
            return Optional.of(
                    "Missing starter lot definition " + lot.orElseThrow().definitionId().value());
        }
        if (lot.isPresent() && quiverDefinitionId().isEmpty()) {
            return Optional.of("Starter ammo requires a Quiver foundation item.");
        }
        return Optional.empty();
    }

    String uniquePayload(StarterItem item) {
        String base =
                "{\"displayRevision\":1,\"starterItem\":true,\"starterFoundation\":\""
                        + name()
                        + "\"";
        if (item.slot() == EquipmentSlot.QUIVER && lot.isPresent()) {
            return base
                    + ",\"quiver\":{\"preparedAmmo\":[\""
                    + lot.orElseThrow().definitionId().value()
                    + "\"],\"selectedIndex\":0}}";
        }
        return base + "}";
    }

    String lotLineage() {
        return "{\"displayRevision\":1,\"starterItem\":true,\"starterFoundation\":\""
                + name()
                + "\"}";
    }

    static StartingFoundation fromPersistentId(String value) {
        Objects.requireNonNull(value, "value");
        return valueOf(value);
    }

    private static StarterItem item(String definitionId, EquipmentSlot slot) {
        return new StarterItem(DefinitionId.of(definitionId), slot);
    }

    record StarterItem(DefinitionId definitionId, EquipmentSlot slot) {
        StarterItem {
            Objects.requireNonNull(definitionId, "definitionId");
            Objects.requireNonNull(slot, "slot");
        }
    }

    record StarterLot(DefinitionId definitionId, long quantity) {
        StarterLot {
            Objects.requireNonNull(definitionId, "definitionId");
            if (quantity < 1) {
                throw new IllegalArgumentException("quantity must be positive");
            }
        }
    }
}
