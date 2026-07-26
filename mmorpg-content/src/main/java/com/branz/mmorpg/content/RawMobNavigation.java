package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;

record RawMobNavigation(
        @JsonProperty("movement_speed") double movementSpeed,
        @JsonProperty("decision_interval_ms") long decisionIntervalMillis,
        @JsonProperty("path_request_interval_ms") long pathRequestIntervalMillis,
        @JsonProperty("can_swim") boolean canSwim) {
}
