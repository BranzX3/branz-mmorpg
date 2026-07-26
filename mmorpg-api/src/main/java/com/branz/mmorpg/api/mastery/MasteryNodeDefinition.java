package com.branz.mmorpg.api.mastery;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentType;
import com.branz.mmorpg.api.stat.AttributeModifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Declarative bounded node in a Combat Mastery DAG. */
public record MasteryNodeDefinition(
        ContentId id,
        ContentId masteryId,
        int treeRevision,
        String branchId,
        int maximumRank,
        int pointCostPerRank,
        int requiredMasteryLevel,
        Map<ContentId, Integer> prerequisites,
        Optional<String> exclusionGroup,
        Optional<ContentId> unlockedSkillId,
        List<AttributeModifier> modifiers) implements ContentDefinition {
    public MasteryNodeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(masteryId, "masteryId");
        Objects.requireNonNull(prerequisites, "prerequisites");
        Objects.requireNonNull(exclusionGroup, "exclusionGroup");
        Objects.requireNonNull(unlockedSkillId, "unlockedSkillId");
        Objects.requireNonNull(modifiers, "modifiers");
        branchId = branchId == null ? "" : branchId.trim();
        prerequisites = Map.copyOf(prerequisites);
        exclusionGroup = exclusionGroup.map(String::trim).filter(value -> !value.isEmpty());
        modifiers = List.copyOf(modifiers);
        if (treeRevision < 1 || branchId.isEmpty() || maximumRank < 1 || maximumRank > 100
                || pointCostPerRank < 1 || pointCostPerRank > 100
                || requiredMasteryLevel < 1 || (modifiers.isEmpty() && unlockedSkillId.isEmpty())) {
            throw new IllegalArgumentException(id + ": invalid mastery node");
        }
        prerequisites.forEach((required, rank) -> {
            if (required.equals(id) || rank == null || rank < 1) {
                throw new IllegalArgumentException(id + ": invalid prerequisite " + required);
            }
        });
    }

    @Override public ContentType type() { return ContentType.COMBAT_MASTERY_NODE; }
}
