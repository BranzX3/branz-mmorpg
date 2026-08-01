package com.branz.mmorpg.persistence.progression;

import com.branz.mmorpg.progression.renown.RenownDecision;
import com.branz.mmorpg.progression.renown.RenownDeedCandidate;
import java.time.Instant;
import java.util.Objects;

public record RenownDeedRecord(
        RenownDeedCandidate candidate, RenownDecision decision, Instant recordedAt) {
    public RenownDeedRecord {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(recordedAt, "recordedAt");
    }
}
