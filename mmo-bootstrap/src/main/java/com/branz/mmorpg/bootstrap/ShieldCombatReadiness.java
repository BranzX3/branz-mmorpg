package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import java.util.Objects;
import java.util.Optional;

/** Authoritative zero-durability gate for an equipped off-hand Shield. */
final class ShieldCombatReadiness {
    static final String BROKEN = "Guard not ready: equipped shield is broken.";
    static final String INVALID = "Guard not ready: shield durability state is invalid.";
    static final String INSTANCE_UNAVAILABLE = "Guard not ready: equipped shield is unavailable.";

    private ShieldCombatReadiness() {}

    static Optional<String> durabilityFailure(
            LoadedCharacterSession character, ItemDefinition shieldDefinition) {
        Objects.requireNonNull(character, "character");
        Objects.requireNonNull(shieldDefinition, "shieldDefinition");
        if (shieldDefinition.shieldProfile().isEmpty()) {
            return Optional.empty();
        }
        if (shieldDefinition.baseMaxDurability().isEmpty()) {
            return Optional.of(INVALID);
        }
        ItemId itemId = character.snapshot().equipment().item(EquipmentSlot.OFF_HAND).orElse(null);
        ItemLocationRecord record =
                itemId == null
                        ? null
                        : character.snapshot().itemRecords().stream()
                                .filter(candidate -> candidate.itemId().equals(itemId))
                                .findFirst()
                                .orElse(null);
        if (record == null || !record.definitionId().equals(shieldDefinition.id())) {
            return Optional.of(INSTANCE_UNAVAILABLE);
        }
        try {
            ItemDurability durability =
                    ItemDurabilityPayloadCodec.decode(
                            record.payloadJson(), shieldDefinition.baseMaxDurability().getAsInt());
            return durability.broken() ? Optional.of(BROKEN) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.of(INVALID);
        }
    }
}
