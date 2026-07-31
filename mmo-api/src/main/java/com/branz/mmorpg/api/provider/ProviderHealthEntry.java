package com.branz.mmorpg.api.provider;

import java.time.Instant;
import java.util.Objects;

public record ProviderHealthEntry(
        String providerId,
        ProviderRequirement requirement,
        ProviderStatus status,
        String message,
        Instant checkedAt) {
    public ProviderHealthEntry {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId is required");
        }
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(checkedAt, "checkedAt");
    }
}
