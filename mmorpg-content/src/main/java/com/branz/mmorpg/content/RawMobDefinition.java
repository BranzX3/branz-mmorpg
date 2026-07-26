package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Set;

record RawMobDefinition(
        String type,
        String id,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("base_stats") Map<String, Double> baseStats,
        RawMobScaling scaling,
        String faction,
        @JsonProperty("target_policy") String targetPolicy,
        RawMobNavigation navigation,
        List<RawMobAbility> abilities,
        @JsonProperty("aggro_range") double aggroRange,
        @JsonProperty("leash_range") double leashRange,
        @JsonProperty("reset_ms") long resetMillis,
        @JsonProperty("home_region") String homeRegion,
        @JsonProperty("status_immunities") Set<String> statusImmunities,
        @JsonProperty("status_resistances") Map<String, Double> statusResistances,
        @JsonProperty("loot_table") String lootTable,
        @JsonProperty("minimum_contribution") double minimumContribution,
        RawMobPresentation presentation) {
}
