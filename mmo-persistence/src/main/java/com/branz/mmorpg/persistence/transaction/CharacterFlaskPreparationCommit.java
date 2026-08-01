package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Atomic Rest commit: consume exact Infusion Stock lots and replace Expedition Flask state. */
public record CharacterFlaskPreparationCommit(
        CharacterId characterId,
        long expectedStateVersion,
        String replacementPayloadJson,
        DefinitionId infusionStockDefinitionId,
        List<LotQuantityConsumption> stockConsumptions) {
    public CharacterFlaskPreparationCommit {
        Objects.requireNonNull(characterId, "characterId");
        if (expectedStateVersion < 0) {
            throw new IllegalArgumentException("expectedStateVersion must not be negative");
        }
        Objects.requireNonNull(replacementPayloadJson, "replacementPayloadJson");
        if (replacementPayloadJson.isBlank()) {
            throw new IllegalArgumentException("replacementPayloadJson must not be blank");
        }
        Objects.requireNonNull(infusionStockDefinitionId, "infusionStockDefinitionId");
        stockConsumptions =
                Objects.requireNonNull(stockConsumptions, "stockConsumptions").stream()
                        .sorted(java.util.Comparator.comparing(value -> value.lotId().value()))
                        .toList();
        Set<com.branz.mmorpg.api.identity.LotId> lots = new HashSet<>();
        for (LotQuantityConsumption consumption : stockConsumptions) {
            if (consumption.expectedOwnerCharacterId().filter(characterId::equals).isEmpty()
                    || consumption.expectedLocation().type()
                            != ValueLocationType.CHARACTER_INVENTORY) {
                throw new IllegalArgumentException(
                        "Infusion Stock must come from the character inventory");
            }
            if (!lots.add(consumption.lotId())) {
                throw new IllegalArgumentException("duplicate Infusion Stock lot consumption");
            }
        }
    }

    public long totalStockConsumed() {
        return stockConsumptions.stream().mapToLong(LotQuantityConsumption::quantity).sum();
    }
}
