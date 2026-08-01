package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;

/** Atomic item use: consume one exact lot unit and replace the expedition effect document. */
public record CharacterConsumableUseCommit(
        CharacterId characterId,
        long expectedStateVersion,
        String replacementPayloadJson,
        DefinitionId consumableDefinitionId,
        LotQuantityConsumption consumption) {
    public CharacterConsumableUseCommit {
        Objects.requireNonNull(characterId, "characterId");
        if (expectedStateVersion < 0) {
            throw new IllegalArgumentException("expectedStateVersion must not be negative");
        }
        Objects.requireNonNull(replacementPayloadJson, "replacementPayloadJson");
        if (replacementPayloadJson.isBlank()) {
            throw new IllegalArgumentException("replacementPayloadJson must not be blank");
        }
        Objects.requireNonNull(consumableDefinitionId, "consumableDefinitionId");
        Objects.requireNonNull(consumption, "consumption");
        if (consumption.quantity() != 1
                || consumption.expectedOwnerCharacterId().filter(characterId::equals).isEmpty()
                || consumption.expectedLocation().type() != ValueLocationType.CHARACTER_INVENTORY) {
            throw new IllegalArgumentException(
                    "Consumable use must consume one owned character-inventory lot unit");
        }
    }
}
