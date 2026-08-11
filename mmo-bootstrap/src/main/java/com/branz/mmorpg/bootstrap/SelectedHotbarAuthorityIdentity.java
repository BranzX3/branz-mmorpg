package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocationType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Stable authoritative identity of the unique MMO item occupying the currently held gameplay slot.
 */
record SelectedHotbarAuthorityIdentity(ItemId itemId, DefinitionId definitionId) {
    SelectedHotbarAuthorityIdentity {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(definitionId, "definitionId");
    }

    static Optional<SelectedHotbarAuthorityIdentity> resolve(
            List<ItemLocationRecord> records, int heldSlot) {
        Objects.requireNonNull(records, "records");
        if (heldSlot < 0 || heldSlot >= ChronicleService.HOTBAR_SLOT) {
            return Optional.empty();
        }
        String slotReference = "slot:" + heldSlot;
        return records.stream()
                .filter(record -> record.location().type() == ValueLocationType.CHARACTER_INVENTORY)
                .filter(
                        record ->
                                record.location()
                                        .reference()
                                        .filter(slotReference::equals)
                                        .isPresent())
                .map(
                        record ->
                                new SelectedHotbarAuthorityIdentity(
                                        record.itemId(), record.definitionId()))
                .findFirst();
    }

    static boolean changed(
            List<ItemLocationRecord> before, List<ItemLocationRecord> after, int heldSlot) {
        return !resolve(before, heldSlot).equals(resolve(after, heldSlot));
    }
}
