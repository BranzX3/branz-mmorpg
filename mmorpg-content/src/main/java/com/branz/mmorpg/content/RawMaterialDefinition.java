package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;

record RawMaterialDefinition(
        String type,
        String id,
        @JsonProperty("display-name") String displayName,
        String category,
        String rarity,
        boolean tradable,
        @JsonProperty("max-stack-size") int maxStackSize
) {
}
