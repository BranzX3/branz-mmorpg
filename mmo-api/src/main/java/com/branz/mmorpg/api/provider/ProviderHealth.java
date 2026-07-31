package com.branz.mmorpg.api.provider;

import java.time.Instant;
import java.util.Objects;

public record ProviderHealth(ProviderStatus status, String message, Instant checkedAt) {
    public ProviderHealth {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(checkedAt, "checkedAt");
    }

    public static ProviderHealth healthy(Instant checkedAt) {
        return new ProviderHealth(ProviderStatus.HEALTHY, "healthy", checkedAt);
    }

    public static ProviderHealth degraded(String message, Instant checkedAt) {
        return new ProviderHealth(ProviderStatus.DEGRADED, message, checkedAt);
    }

    public static ProviderHealth unavailable(String message, Instant checkedAt) {
        return new ProviderHealth(ProviderStatus.UNAVAILABLE, message, checkedAt);
    }
}
