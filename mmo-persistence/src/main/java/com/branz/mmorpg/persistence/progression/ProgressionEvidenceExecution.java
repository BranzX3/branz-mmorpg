package com.branz.mmorpg.persistence.progression;

import java.util.Objects;
import java.util.Optional;

public record ProgressionEvidenceExecution(
        ProgressionEvidenceRecord evidence,
        Optional<ProgressionTrackRecord> track,
        boolean replayed) {
    public ProgressionEvidenceExecution {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(track, "track");
    }
}
