package com.branz.mmorpg.persistence.progression;

import com.branz.mmorpg.progression.teaching.TeachingCompletion;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TeachingCompletionRecord(
        TeachingCompletion completion, UUID deedId, String contentVersion, Instant completedAt) {
    public TeachingCompletionRecord {
        Objects.requireNonNull(completion, "completion");
        Objects.requireNonNull(deedId, "deedId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(completedAt, "completedAt");
        if (contentVersion.isBlank()) {
            throw new IllegalArgumentException("contentVersion must not be blank");
        }
    }
}
