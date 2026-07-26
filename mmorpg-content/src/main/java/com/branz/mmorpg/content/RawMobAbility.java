package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

record RawMobAbility(
        String skill,
        double weight,
        @JsonProperty("minimum_range") double minimumRange,
        @JsonProperty("maximum_range") double maximumRange,
        @JsonProperty("maximum_health_fraction") double maximumHealthFraction,
        @JsonProperty("required_target_tags") Set<String> requiredTargetTags) {
}
