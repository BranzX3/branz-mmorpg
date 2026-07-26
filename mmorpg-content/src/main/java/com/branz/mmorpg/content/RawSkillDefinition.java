package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Set;

record RawSkillDefinition(
        String type,
        String id,
        @JsonProperty("display-name") String displayName,
        @JsonProperty("input-slot") String inputSlot,
        Set<String> tags,
        @JsonProperty("cast-ms") long castMillis,
        @JsonProperty("active-ms") long activeMillis,
        @JsonProperty("recovery-ms") long recoveryMillis,
        @JsonProperty("cooldown-ms") long cooldownMillis,
        @JsonProperty("cooldown-group") String cooldownGroup,
        Map<String, Double> costs,
        @JsonProperty("interrupt-refund") double interruptRefund,
        double range,
        @JsonProperty("requires-line-of-sight") boolean requiresLineOfSight,
        List<RawEffectNode> effects,
        @JsonProperty("root-effect") String rootEffect) {

    record RawEffectNode(
            String id,
            String type,
            Map<String, Double> numbers,
            Map<String, String> values,
            List<String> children) {
    }
}
