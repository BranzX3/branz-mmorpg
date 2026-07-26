package com.branz.mmorpg.api.lifeskill;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentType;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One bounded node in a Life Skill mastery DAG. */
public record LifeSkillNodeDefinition(
        ContentId id,
        ContentId skillId,
        String displayName,
        int maximumRank,
        int pointCostPerRank,
        int requiredLevel,
        Map<ContentId, Integer> prerequisites,
        Effect effect) implements ContentDefinition {

    public LifeSkillNodeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(skillId, "skillId");
        displayName = displayName == null || displayName.isBlank() ? id.value() : displayName.trim();
        if (maximumRank < 1 || pointCostPerRank < 1 || requiredLevel < 1) {
            throw invalid(id, "rank, point cost, and required level must be positive");
        }
        Objects.requireNonNull(prerequisites, "prerequisites");
        prerequisites.forEach((node, rank) -> {
            if (node == null || rank == null || rank < 1) {
                throw invalid(id, "prerequisite ranks must be positive");
            }
        });
        Objects.requireNonNull(effect, "effect");
        prerequisites = Map.copyOf(prerequisites);
    }

    @Override
    public ContentType type() {
        return ContentType.LIFE_SKILL_NODE;
    }

    public record Effect(String type, Set<String> targetTags,
                         double percentPerRank, double capPercent) {
        public Effect {
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("effect type must not be blank");
            }
            Objects.requireNonNull(targetTags, "targetTags");
            if (!Double.isFinite(percentPerRank) || percentPerRank < 0.0
                    || !Double.isFinite(capPercent) || capPercent < 0.0
                    || percentPerRank > capPercent || capPercent > 100.0) {
                throw new IllegalArgumentException("Life Skill effects must be bounded to [0,100]");
            }
            targetTags = Set.copyOf(targetTags);
        }
    }

    private static MMOException invalid(ContentId id, String detail) {
        return new MMOException(ErrorCode.CONTENT_INVALID, id + ": " + detail);
    }
}
