package com.branz.mmorpg.scenes;

import java.util.Objects;
import java.util.UUID;

/** One value-transfer intent held only by Scene preview state until explicit confirmation. */
public record QuiverAmmoTransferPreview(
        UUID sourceLotId, long quantity, QuiverTransferDirection direction) {
    public QuiverAmmoTransferPreview {
        Objects.requireNonNull(sourceLotId, "sourceLotId");
        if (quantity < 1 || quantity > 4096) {
            throw new IllegalArgumentException(
                    "Quiver transfer quantity must be between 1 and 4096");
        }
        Objects.requireNonNull(direction, "direction");
    }
}
