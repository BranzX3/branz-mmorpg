package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import java.util.Objects;
import java.util.Optional;

/** Pure guard-readiness rule for authoritative durable shield state. */
final class ShieldCombatReadiness {
    static final String BROKEN = "Guard not ready: equipped shield is broken.";
    static final String INVALID = "Guard not ready: shield durability state is invalid.";
    static final String INSTANCE_UNAVAILABLE = "Guard not ready: equipped shield instance is unavailable.";

    private ShieldCombatReadiness() {}

    static Optional<String> durabilityFailure(
            LoadedCharacterSession character, ItemDefinition shield) {
        Objects.requireNonNull(character, "character");
        Objects.requireNonNull(shield, "shield");
        if (shield.shieldProfile().isEmpty() || shield.baseMaxDurability().isEmpty()) {
            return Optional.empty();
        }
        ItemId itemId = character.snapshot().equipment().item(EquipmentSlot.OFF_HAND).orElse(null);
        ItemLocationRecord record =
                itemId == null
                        ? null
                        : character.snapshot().itemRecords().stream()
                                .filter(candidate -> candidate.itemId().equals(itemId))
                                .findFirst()
                                .orElse(null);
        if (record == null || !record.definitionId().equals(shield.id())) {
            return Optional.of(INSTANCE_UNAVAILABLE);
        }
        return durabilityFailure(shield, record.payloadJson());
    }

    static Optional<String> durabilityFailure(ItemDefinition shield, String payloadJson) {
        Objects.requireNonNull(shield, "shield");
        Objects.requireNonNull(payloadJson, "payloadJson");
        if (shield.shieldProfile().isEmpty() || shield.baseMaxDurability().isEmpty()) {
            return Optional.empty();
        }
        try {
            ItemDurability durability =
                    ItemDurabilityPayloadCodec.decode(
                            payloadJson, shield.baseMaxDurability().getAsInt());
            return durability.broken() ? Optional.of(BROKEN) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.of(INVALID);
        }
    }
}
