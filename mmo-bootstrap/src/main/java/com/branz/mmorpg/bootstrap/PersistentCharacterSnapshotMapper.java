package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.items.equipment.EquipmentLoadout;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.items.projection.ExpectedProjection;
import com.branz.mmorpg.items.projection.ProjectionValueType;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import com.branz.mmorpg.persistence.transaction.ValueLocationType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Converts repository rows into the one immutable snapshot used by Paper and Scene. */
final class PersistentCharacterSnapshotMapper {
    private static final ObjectMapper JSON = new ObjectMapper();

    private PersistentCharacterSnapshotMapper() {}

    static PersistentCharacterSnapshot map(
            List<ItemLocationRecord> items, List<LotLocationRecord> lots) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(lots, "lots");
        List<ExpectedProjection> inventory = new ArrayList<>();
        EquipmentLoadout equipment = EquipmentLoadout.empty();
        for (ItemLocationRecord item : items) {
            if (item.location().type() == ValueLocationType.CHARACTER_INVENTORY) {
                inventory.add(itemProjection(item, inventorySlot(item.location())));
            } else if (item.location().type() == ValueLocationType.NATIVE_EQUIPPED
                    || item.location().type() == ValueLocationType.VIRTUAL_EQUIPPED) {
                EquipmentSlot slot =
                        EquipmentSlot.valueOf(item.location().reference().orElseThrow());
                equipment = equipment.with(slot, Optional.of(new ItemId(item.itemId().value())));
            }
        }
        for (LotLocationRecord lot : lots) {
            if (lot.location().type() == ValueLocationType.CHARACTER_INVENTORY) {
                if (lot.quantity() > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException(
                            "Lot quantity exceeds projection range: " + lot.lotId().value());
                }
                inventory.add(
                        new ExpectedProjection(
                                lot.lotId().value(),
                                lot.definitionId(),
                                ProjectionValueType.STACKABLE_LOT,
                                inventorySlot(lot.location()),
                                (int) lot.quantity(),
                                lot.version(),
                                displayRevision(lot.lineageJson()),
                                lot.contentVersion(),
                                testProvenance(lot.lineageJson())));
            }
        }
        return new PersistentCharacterSnapshot(inventory, equipment, items, lots);
    }

    private static int inventorySlot(ValueLocation location) {
        String reference = location.reference().orElseThrow();
        if (!reference.startsWith("slot:")) {
            throw new IllegalArgumentException("Inventory location must use slot:<number>");
        }
        try {
            int slot = Integer.parseInt(reference.substring("slot:".length()));
            if (slot < 0 || slot > 35 || slot == ChronicleService.HOTBAR_SLOT) {
                throw new IllegalArgumentException("Invalid MMO inventory slot " + slot);
            }
            return slot;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid inventory slot reference " + reference);
        }
    }

    static ExpectedProjection itemProjection(ItemLocationRecord item, int logicalSlot) {
        Objects.requireNonNull(item, "item");
        return new ExpectedProjection(
                item.itemId().value(),
                item.definitionId(),
                ProjectionValueType.UNIQUE_ITEM,
                logicalSlot,
                1,
                item.version(),
                displayRevision(item.payloadJson()),
                item.contentVersion(),
                testProvenance(item.payloadJson()));
    }

    private static long displayRevision(String json) {
        JsonNode root = parse(json);
        long revision = root.path("displayRevision").asLong(1);
        if (revision < 1) {
            throw new IllegalArgumentException("displayRevision must be positive");
        }
        return revision;
    }

    private static Optional<String> testProvenance(String json) {
        String provenance = parse(json).path("testProvenance").asText("");
        return provenance.isBlank() ? Optional.empty() : Optional.of(provenance);
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("Persisted item payload is invalid JSON");
        }
    }
}
