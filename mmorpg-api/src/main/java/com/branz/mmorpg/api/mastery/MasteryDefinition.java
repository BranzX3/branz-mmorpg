package com.branz.mmorpg.api.mastery;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentType;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import java.util.Objects;

/** Data-driven combat mastery curve with a bounded power contribution. */
public record MasteryDefinition(
        ContentId id,
        String displayName,
        Kind kind,
        ContentId parentId,
        int maximumLevel,
        double curveBase,
        double curveExponent,
        double maximumPowerBonus) implements ContentDefinition {

    public enum Kind { FAMILY, WEAPON_TYPE, SKILL }

    public MasteryDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        displayName = displayName == null || displayName.isBlank() ? id.value() : displayName.trim();
        if (kind != Kind.FAMILY && parentId == null) {
            throw invalid(id, "non-family mastery requires a parent");
        }
        if (maximumLevel < 1 || maximumLevel > 10_000
                || !Double.isFinite(curveBase) || curveBase <= 0.0
                || !Double.isFinite(curveExponent) || curveExponent <= 0.0) {
            throw invalid(id, "invalid mastery curve");
        }
        if (!Double.isFinite(maximumPowerBonus)
                || maximumPowerBonus < 0.0 || maximumPowerBonus > 0.25) {
            throw invalid(id, "maximumPowerBonus must be in [0,0.25]");
        }
    }

    @Override
    public ContentType type() {
        return ContentType.COMBAT_MASTERY;
    }

    private static MMOException invalid(ContentId id, String message) {
        return new MMOException(ErrorCode.CONTENT_INVALID, id + ": " + message);
    }
}
