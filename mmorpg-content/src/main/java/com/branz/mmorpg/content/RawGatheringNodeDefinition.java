package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Set;

record RawGatheringNodeDefinition(
        String type,
        String id,
        @JsonProperty("display_name") String displayName,
        String skill,
        String tier,
        @JsonProperty("base_xp") long baseXp,
        @JsonProperty("required_tool_tag") String requiredToolTag,
        @JsonProperty("required_level") int requiredLevel,
        @JsonProperty("harvest_time_ms") long harvestTimeMillis,
        @JsonProperty("respawn_seconds") long respawnSeconds,
        @JsonProperty("respawn_jitter_seconds") long respawnJitterSeconds,
        Set<String> tags,
        RawGatheringPresentation presentation,
        List<RawGatheringYield> yields) {
}
