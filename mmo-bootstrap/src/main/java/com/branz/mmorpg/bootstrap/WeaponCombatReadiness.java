package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import java.util.Objects;
import java.util.Optional;

/** Pure readiness rule for authoritative durable weapon state. */
final class WeaponCombatReadiness {
    static final String BROKEN = "Combat not ready: equipped weapon is broken.";
    static final String INVALID = "Combat not ready: weapon durability state is invalid.";
    static final String INSTANCE_UNAVAILABLE =
            "Combat not ready: equipped weapon instance is unavailable.";

    private WeaponCombatReadiness() {}

    static Optional<String> durabilityFailure(
            LoadedCharacterSession character, ItemDefinition weapon) {
        Objects.requireNonNull(character, "character");
        Objects.requireNonNull(weapon, "weapon");
        if (weapon.baseMaxDurability().isEmpty()) {
            return Optional.empty();
        }
        ItemId itemId =
                character.snapshot().equipment().item(EquipmentSlot.MAIN_HAND).orElse(null);
        ItemLocationRecord record =
                itemId == null
                        ? null
                        : character.snapshot().itemRecords().stream()
                                .filter(candidate -> candidate.itemId().equals(itemId))
                                .findFirst()
                                .orElse(null);
        if (record == null || !record.definitionId().equals(weapon.id())) {
            return Optional.of(INSTANCE_UNAVAILABLE);
        }
        return durabilityFailure(weapon, record.payloadJson());
    }

    static Optional<String> durabilityFailure(ItemDefinition weapon, String payloadJson) {
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(payloadJson, "payloadJson");
        if (weapon.baseMaxDurability().isEmpty()) {
            return Optional.empty();
        }
        try {
            WeaponDurability durability =
                    WeaponPayloadCodec.decode(
                            payloadJson, weapon.baseMaxDurability().getAsInt());
            return durability.broken() ? Optional.of(BROKEN) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.of(INVALID);
        }
    }
}
