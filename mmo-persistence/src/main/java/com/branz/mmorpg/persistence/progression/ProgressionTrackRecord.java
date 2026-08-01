package com.branz.mmorpg.persistence.progression;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.progression.evidence.ProgressionTrack;
import java.time.Instant;
import java.util.Objects;

public record ProgressionTrackRecord(
        CharacterId characterId,
        ProgressionTrack track,
        double evidence,
        long version,
        Instant updatedAt) {
    public ProgressionTrackRecord {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(track, "track");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!Double.isFinite(evidence) || evidence < 0.0 || evidence > 1000.0) {
            throw new IllegalArgumentException("evidence must be between 0 and 1000");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }
}
