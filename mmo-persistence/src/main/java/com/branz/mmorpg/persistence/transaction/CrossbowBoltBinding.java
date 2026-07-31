package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;

/** Atomic BOLT_PLACED commit: bind item payload and consume one exact Quiver lot unit. */
public record CrossbowBoltBinding(
        ItemPayloadUpdate crossbowUpdate,
        LotQuantityConsumption boltConsumption,
        DefinitionId expectedBoltDefinitionId) {
    public CrossbowBoltBinding {
        Objects.requireNonNull(crossbowUpdate, "crossbowUpdate");
        Objects.requireNonNull(boltConsumption, "boltConsumption");
        Objects.requireNonNull(expectedBoltDefinitionId, "expectedBoltDefinitionId");
        if (!crossbowUpdate
                        .expectedOwnerCharacterId()
                        .equals(boltConsumption.expectedOwnerCharacterId())
                || crossbowUpdate.expectedLocation().type() != ValueLocationType.NATIVE_EQUIPPED
                || !crossbowUpdate
                        .expectedLocation()
                        .reference()
                        .filter("MAIN_HAND"::equals)
                        .isPresent()
                || boltConsumption.expectedLocation().type() != ValueLocationType.QUIVER
                || boltConsumption.quantity() != 1) {
            throw new IllegalArgumentException(
                    "Crossbow bolt binding requires one owner, equipped main hand and one Quiver bolt");
        }
    }
}
