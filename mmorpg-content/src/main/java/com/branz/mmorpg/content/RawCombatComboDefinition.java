package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Set;

record RawCombatComboDefinition(
        String type,
        String id,
        @JsonProperty("required-tags") Set<String> requiredTags,
        List<RawStep> steps,
        @JsonProperty("reset-timeout-millis") long resetTimeoutMillis,
        int priority,
        @JsonProperty("consumes-input") boolean consumesInput,
        @JsonProperty("result-skill") String resultSkill) {
    record RawStep(String input,
                   @JsonProperty("minimum-delay-millis") long minimumDelayMillis,
                   @JsonProperty("maximum-delay-millis") long maximumDelayMillis) {
    }
}
