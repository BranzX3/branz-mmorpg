package com.branz.mmorpg.api.character;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentType;
import com.branz.mmorpg.api.stat.AttributeModifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** One validated rankable node in a permanent class's directed acyclic tree. */
public record ClassSkillNodeDefinition(
        ContentId id,
        ContentId classId,
        int treeRevision,
        String branchId,
        ClassSkillNodeType nodeType,
        int maximumRank,
        int pointCostPerRank,
        int requiredClassLevel,
        Map<ContentId, Integer> prerequisites,
        Optional<String> exclusionGroup,
        Optional<ContentId> unlockedSkillId,
        List<AttributeModifier> modifiers) implements ContentDefinition {

    public ClassSkillNodeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(classId, "classId");
        Objects.requireNonNull(nodeType, "nodeType");
        Objects.requireNonNull(prerequisites, "prerequisites");
        Objects.requireNonNull(exclusionGroup, "exclusionGroup");
        Objects.requireNonNull(unlockedSkillId, "unlockedSkillId");
        Objects.requireNonNull(modifiers, "modifiers");
        branchId = branchId == null ? "" : branchId.trim();
        exclusionGroup = exclusionGroup.map(String::trim).filter(value -> !value.isEmpty());
        prerequisites = Map.copyOf(prerequisites);
        modifiers = List.copyOf(modifiers);
        if (treeRevision < 1 || branchId.isEmpty() || maximumRank < 1 || maximumRank > 100
                || pointCostPerRank < 1 || pointCostPerRank > 100
                || requiredClassLevel < 1) {
            throw new IllegalArgumentException(id + ": invalid class tree node bounds");
        }
        prerequisites.forEach((required, rank) -> {
            if (required.equals(id) || rank == null || rank < 1) {
                throw new IllegalArgumentException(id + ": invalid prerequisite " + required);
            }
        });
        boolean unlock = nodeType == ClassSkillNodeType.ACTIVE_UNLOCK
                || nodeType == ClassSkillNodeType.ULTIMATE_UNLOCK;
        if (unlock != unlockedSkillId.isPresent()) {
            throw new IllegalArgumentException(id + ": unlock node/skill mismatch");
        }
        if (modifiers.isEmpty() && !unlock && nodeType != ClassSkillNodeType.KEYSTONE) {
            throw new IllegalArgumentException(id + ": non-unlock node requires an effect");
        }
    }

    @Override public ContentType type() { return ContentType.CLASS_SKILL_NODE; }
}
