package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;

record RawMobPresentation(
        @JsonProperty("entity_type") String entityType,
        @JsonProperty("model_id") String modelId) {
}
