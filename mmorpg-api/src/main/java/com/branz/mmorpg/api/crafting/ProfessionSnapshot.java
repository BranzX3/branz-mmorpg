package com.branz.mmorpg.api.crafting;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Objects;

public record ProfessionSnapshot(
        ContentId professionId,
        int level,
        long totalXp,
        Instant updatedAt) {
    public ProfessionSnapshot {
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (level < 1 || totalXp < 0) throw new IllegalArgumentException("invalid profession state");
    }

    public static ProfessionSnapshot untrained(ContentId id, Instant now) {
        return new ProfessionSnapshot(id, 1, 0, now);
    }
}
