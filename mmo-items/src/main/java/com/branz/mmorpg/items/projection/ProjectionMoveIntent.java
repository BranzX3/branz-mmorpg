package com.branz.mmorpg.items.projection;

import java.util.Objects;
import java.util.UUID;

public record ProjectionMoveIntent(
        UUID valueId,
        ProjectionValueType valueType,
        int sourceSlot,
        int destinationSlot,
        boolean swap) {
    public ProjectionMoveIntent {
        Objects.requireNonNull(valueId, "valueId");
        Objects.requireNonNull(valueType, "valueType");
        if (sourceSlot < 0 || destinationSlot < 0 || sourceSlot == destinationSlot) {
            throw new IllegalArgumentException(
                    "projection move requires distinct non-negative slots");
        }
        if (swap && valueType != ProjectionValueType.UNIQUE_ITEM) {
            throw new IllegalArgumentException("stackable lot swap is not supported");
        }
    }

    public ProjectionMoveIntent(
            UUID valueId, int sourceSlot, int destinationSlot, boolean swap) {
        this(valueId, ProjectionValueType.UNIQUE_ITEM, sourceSlot, destinationSlot, swap);
    }
}
