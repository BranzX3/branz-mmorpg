package com.branz.mmorpg.items.consumable;

import java.util.Objects;

/** Immutable item-content contract for one category-scoped consumable effect. */
public record ConsumableDefinitionProfile(
        ConsumableCategory category,
        ConsumableUseProfile useProfile,
        int effectDurationTicks,
        boolean rare) {
    public ConsumableDefinitionProfile {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(useProfile, "useProfile");
        if (effectDurationTicks < 1) {
            throw new IllegalArgumentException("effectDurationTicks must be positive");
        }
    }
}
