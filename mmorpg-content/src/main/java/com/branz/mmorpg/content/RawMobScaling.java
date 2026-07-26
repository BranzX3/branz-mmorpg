package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;

record RawMobScaling(
        @JsonProperty("health_per_level") double healthPerLevel,
        @JsonProperty("power_per_level") double powerPerLevel,
        @JsonProperty("maximum_multiplier") double maximumMultiplier) {
}
