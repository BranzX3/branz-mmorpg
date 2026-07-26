package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

record RawClassSkillNodeDefinition(
        String type,
        String id,
        @JsonProperty("class-id") String classId,
        @JsonProperty("tree-revision") int treeRevision,
        @JsonProperty("branch-id") String branchId,
        @JsonProperty("node-type") String nodeType,
        @JsonProperty("maximum-rank") int maximumRank,
        @JsonProperty("point-cost-per-rank") int pointCostPerRank,
        @JsonProperty("required-class-level") int requiredClassLevel,
        Map<String, Integer> prerequisites,
        @JsonProperty("exclusion-group") String exclusionGroup,
        @JsonProperty("unlocked-skill") String unlockedSkill,
        List<RawStatusDefinition.RawModifier> modifiers) {
}
