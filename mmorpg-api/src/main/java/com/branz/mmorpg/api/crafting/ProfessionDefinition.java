package com.branz.mmorpg.api.crafting;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentType;
import java.util.Objects;

public record ProfessionDefinition(
        ContentId id,
        String displayName,
        int maximumLevel,
        double curveBase,
        double curveExponent) implements ContentDefinition {

    public ProfessionDefinition {
        Objects.requireNonNull(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        if (displayName.isEmpty() || maximumLevel < 1
                || !Double.isFinite(curveBase) || curveBase <= 0
                || !Double.isFinite(curveExponent) || curveExponent <= 0) {
            throw new IllegalArgumentException("invalid profession " + id);
        }
    }

    @Override public ContentType type() { return ContentType.PROFESSION; }
}
