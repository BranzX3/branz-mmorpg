package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

record RawLifeSkillDefinition(
        String type,
        String id,
        @JsonProperty("display-name") String displayName,
        @JsonProperty("maximum-level") int maximumLevel,
        @JsonProperty("curve-base") double curveBase,
        @JsonProperty("curve-exponent") double curveExponent,
        @JsonProperty("point-milestones") Set<Integer> pointMilestones) {
}
