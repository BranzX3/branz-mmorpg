package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

record RawMasteryNodeDefinition(
        String type,
        String id,
        @JsonProperty("mastery-id") String masteryId,
        @JsonProperty("tree-revision") int treeRevision,
        @JsonProperty("branch-id") String branchId,
        @JsonProperty("maximum-rank") int maximumRank,
        @JsonProperty("point-cost-per-rank") int pointCostPerRank,
        @JsonProperty("required-mastery-level") int requiredMasteryLevel,
        Map<String, Integer> prerequisites,
        @JsonProperty("exclusion-group") String exclusionGroup,
        @JsonProperty("unlocked-skill") String unlockedSkill,
        List<RawStatusDefinition.RawModifier> modifiers) {
}
