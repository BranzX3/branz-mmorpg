package com.branz.mmorpg.api.mastery;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Objects;

public record MasterySnapshot(
        ContentId masteryId,
        int level,
        long totalXp,
        Instant updatedAt) {

    public MasterySnapshot {
        Objects.requireNonNull(masteryId, "masteryId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (level < 1 || totalXp < 0) {
            throw new IllegalArgumentException("mastery level/XP is invalid");
        }
    }

    public static MasterySnapshot untrained(ContentId id, Instant at) {
        return new MasterySnapshot(id, 1, 0L, at);
    }
}
