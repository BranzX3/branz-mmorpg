package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

record RawLootDefinition(
        String type,
        String id,
        @JsonProperty("display-name") String displayName,
        String ownership,
        @JsonProperty("weighted-rolls") int weightedRolls,
        @JsonProperty("contribution-required") boolean contributionRequired,
        List<RawLootEntry> entries) {
}
