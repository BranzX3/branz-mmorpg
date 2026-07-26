package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

record RawLootEntry(
        String id,
        @JsonProperty("item-id") String itemId,
        double weight,
        boolean guaranteed,
        @JsonProperty("minimum-quantity") long minimumQuantity,
        @JsonProperty("maximum-quantity") long maximumQuantity,
        @JsonProperty("required-conditions") Set<String> requiredConditions,
        @JsonProperty("pity-after") int pityAfter,
        @JsonProperty("per-roll-cap") long perRollCap) {
}
