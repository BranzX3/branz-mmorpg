package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Set;

record RawStatusDefinition(
        String type,
        String id,
        @JsonProperty("display-name") String displayName,
        String category,
        @JsonProperty("stack-policy") String stackPolicy,
        @JsonProperty("max-stacks") int maxStacks,
        @JsonProperty("duration-ms") long durationMillis,
        @JsonProperty("periodic-interval-ms") long periodicIntervalMillis,
        double potency,
        List<RawModifier> modifiers,
        @JsonProperty("dispel-tags") Set<String> dispelTags,
        @JsonProperty("crowd-control") String crowdControl,
        @JsonProperty("offline-policy") String offlinePolicy) {

    record RawModifier(
            String id,
            String attribute,
            String operation,
            double value,
            @JsonProperty("stacking-group") String stackingGroup,
            int priority) {
    }
}
