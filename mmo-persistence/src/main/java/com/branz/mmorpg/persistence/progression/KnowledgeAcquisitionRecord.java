package com.branz.mmorpg.persistence.progression;

import java.time.Instant;
import java.util.Objects;

public record KnowledgeAcquisitionRecord(KnowledgeAcquisitionRequest request, Instant acquiredAt) {
    public KnowledgeAcquisitionRecord {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(acquiredAt, "acquiredAt");
    }
}
