package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;

record RawRecipeOutput(
        String item,
        long quantity,
        String binding,
        @JsonProperty("quality_policy") String qualityPolicy) {
}
