package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Set;

record RawLifeSkillNodeDefinition(
        String type,
        String id,
        String skill,
        @JsonProperty("display-name") String displayName,
        @JsonProperty("max-rank") int maximumRank,
        @JsonProperty("point-cost-per-rank") int pointCostPerRank,
        @JsonProperty("requires-level") int requiredLevel,
        Map<String, Integer> prerequisites,
        RawEffect effect) {

    record RawEffect(
            String type,
            @JsonProperty("target-tags") Set<String> targetTags,
            @JsonProperty("percent-per-rank") double percentPerRank,
            @JsonProperty("cap-percent") double capPercent) {
    }
}
