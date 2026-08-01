package com.branz.mmorpg.persistence.progression;

import com.branz.mmorpg.progression.evidence.EvidenceCandidate;
import com.branz.mmorpg.progression.evidence.EvidenceDecision;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProgressionEvidenceRecord(
        UUID batchId, EvidenceCandidate candidate, EvidenceDecision decision, Instant recordedAt) {
    public ProgressionEvidenceRecord {
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(recordedAt, "recordedAt");
    }
}
