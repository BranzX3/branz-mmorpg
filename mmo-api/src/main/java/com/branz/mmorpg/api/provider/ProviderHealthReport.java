package com.branz.mmorpg.api.provider;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ProviderHealthReport(
        ProviderReadiness readiness, Instant checkedAt, List<ProviderHealthEntry> providers) {
    public ProviderHealthReport {
        Objects.requireNonNull(readiness, "readiness");
        Objects.requireNonNull(checkedAt, "checkedAt");
        providers = List.copyOf(providers);
    }

    public boolean acceptsSessions() {
        return readiness != ProviderReadiness.MAINTENANCE;
    }
}
