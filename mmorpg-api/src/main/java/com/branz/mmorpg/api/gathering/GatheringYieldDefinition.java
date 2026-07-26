package com.branz.mmorpg.api.gathering;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Objects;

public record GatheringYieldDefinition(
        ContentId itemId,
        long minimumAmount,
        long maximumAmount,
        double chance) {

    public GatheringYieldDefinition {
        Objects.requireNonNull(itemId, "itemId");
        if (minimumAmount < 1 || maximumAmount < minimumAmount
                || !Double.isFinite(chance) || chance <= 0 || chance > 1) {
            throw new IllegalArgumentException("invalid gathering yield");
        }
    }
}
