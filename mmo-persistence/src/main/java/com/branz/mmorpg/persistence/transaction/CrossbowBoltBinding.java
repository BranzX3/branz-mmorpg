package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;

/** Atomic BOLT_PLACED commit: bind one physical Crossbow payload and consume one Quiver bolt. */
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
                || crossbowUpdate.expectedLocation().type() != ValueLocationType.CHARACTER_INVENTORY
                || boltConsumption.expectedLocation().type() != ValueLocationType.QUIVER
                || boltConsumption.quantity() != 1) {
            throw new IllegalArgumentException(
                    "Crossbow bolt binding requires one owner, physical inventory Crossbow and one Quiver bolt");
        }
    }
}
