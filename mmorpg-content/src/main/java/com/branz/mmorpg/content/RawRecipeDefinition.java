package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

record RawRecipeDefinition(
        String type,
        String id,
        @JsonProperty("display_name") String displayName,
        Map<String, Long> inputs,
        @JsonProperty("optional_catalysts") Map<String, Long> optionalCatalysts,
        @JsonProperty("coin_fee") long coinFee,
        @JsonProperty("station_tag") String stationTag,
        String profession,
        @JsonProperty("required_profession_level") int requiredProfessionLevel,
        @JsonProperty("duration_ms") long durationMillis,
        RawRecipeOutput output,
        @JsonProperty("profession_xp") long professionXp,
        @JsonProperty("trivial_after_level") int trivialAfterLevel) {
}
