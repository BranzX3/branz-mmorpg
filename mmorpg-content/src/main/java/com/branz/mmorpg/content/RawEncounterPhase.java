package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

record RawEncounterPhase(
        String id,
        @JsonProperty("health_fraction_threshold") double healthFractionThreshold,
        @JsonProperty("ability_ids") Set<String> abilityIds,
        @JsonProperty("add_mob_ids") Set<String> addMobIds,
        @JsonProperty("pressure_multiplier") double pressureMultiplier) {
}
