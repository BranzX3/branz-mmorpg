package com.branz.mmorpg.api.lifeskill;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentType;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import java.util.Objects;
import java.util.Set;

/** Data-driven level curve and milestone points for one Life Skill. */
public record LifeSkillDefinition(
        ContentId id,
        String displayName,
        int maximumLevel,
        double curveBase,
        double curveExponent,
        Set<Integer> pointMilestones) implements ContentDefinition {

    public LifeSkillDefinition {
        Objects.requireNonNull(id, "id");
        displayName = displayName == null || displayName.isBlank() ? id.value() : displayName.trim();
        if (maximumLevel < 1 || maximumLevel > 10_000) {
            throw invalid(id, "maximumLevel must be in [1,10000]");
        }
        if (!Double.isFinite(curveBase) || curveBase <= 0.0
                || !Double.isFinite(curveExponent) || curveExponent <= 0.0) {
            throw invalid(id, "curve values must be positive and finite");
        }
        Objects.requireNonNull(pointMilestones, "pointMilestones");
        if (pointMilestones.stream().anyMatch(level -> level == null || level < 2
                || level > maximumLevel)) {
            throw invalid(id, "point milestones must be between level 2 and maximumLevel");
        }
        pointMilestones = Set.copyOf(pointMilestones);
    }

    @Override
    public ContentType type() {
        return ContentType.LIFE_SKILL;
    }

    private static MMOException invalid(ContentId id, String detail) {
        return new MMOException(ErrorCode.CONTENT_INVALID, id + ": " + detail);
    }
}
