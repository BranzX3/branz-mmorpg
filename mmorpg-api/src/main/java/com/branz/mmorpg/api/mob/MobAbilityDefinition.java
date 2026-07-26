package com.branz.mmorpg.api.mob;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Objects;
import java.util.Set;

public record MobAbilityDefinition(
        ContentId skillId,
        double weight,
        double minimumRange,
        double maximumRange,
        double maximumHealthFraction,
        Set<String> requiredTargetTags) {
    public MobAbilityDefinition {
        Objects.requireNonNull(skillId, "skillId");
        requiredTargetTags = Set.copyOf(requiredTargetTags);
        if (!Double.isFinite(weight) || weight <= 0
                || !Double.isFinite(minimumRange) || minimumRange < 0
                || !Double.isFinite(maximumRange) || maximumRange < minimumRange
                || !Double.isFinite(maximumHealthFraction)
                || maximumHealthFraction <= 0 || maximumHealthFraction > 1) {
            throw new IllegalArgumentException("invalid mob ability");
        }
    }
}
