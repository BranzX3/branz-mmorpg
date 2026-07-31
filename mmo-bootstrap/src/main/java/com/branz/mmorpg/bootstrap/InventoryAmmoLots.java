package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocationType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic view of consumable ammo lots currently owned in character inventory. */
final class InventoryAmmoLots {
    private InventoryAmmoLots() {}

    static Optional<LotLocationRecord> select(
            List<LotLocationRecord> records, DefinitionId definitionId) {
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(definitionId, "definitionId");
        return records.stream()
                .filter(record -> record.definitionId().equals(definitionId))
                .filter(record -> record.quantity() > 0)
                .filter(record -> record.location().type() == ValueLocationType.CHARACTER_INVENTORY)
                .min(
                        java.util.Comparator.comparingInt(InventoryAmmoLots::inventorySlot)
                                .thenComparing(record -> record.lotId().value()));
    }

    static long quantity(List<LotLocationRecord> records, DefinitionId definitionId) {
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(definitionId, "definitionId");
        long quantity = 0;
        for (LotLocationRecord record : records) {
            if (record.definitionId().equals(definitionId)
                    && record.location().type() == ValueLocationType.CHARACTER_INVENTORY) {
                if (Long.MAX_VALUE - quantity < record.quantity()) {
                    return Long.MAX_VALUE;
                }
                quantity += record.quantity();
            }
        }
        return quantity;
    }

    private static int inventorySlot(LotLocationRecord record) {
        String reference = record.location().reference().orElseThrow();
        if (!reference.startsWith("slot:")) {
            throw new IllegalArgumentException("Ammo inventory location must use slot:<number>");
        }
        try {
            int slot = Integer.parseInt(reference.substring("slot:".length()));
            if (slot < 0 || slot > 35) {
                throw new IllegalArgumentException("Invalid ammo inventory slot " + reference);
            }
            return slot;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid ammo inventory slot " + reference);
        }
    }
}
