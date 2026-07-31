package com.branz.mmorpg.api.provider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable provider registry with deterministic aggregate health reporting. */
public final class ProviderRegistry {
    private final Map<String, Provider> providers;

    private ProviderRegistry(Map<String, Provider> providers) {
        this.providers = Collections.unmodifiableMap(new LinkedHashMap<>(providers));
    }

    public static ProviderRegistry of(Collection<? extends Provider> providers) {
        Objects.requireNonNull(providers, "providers");
        List<? extends Provider> ordered =
                providers.stream()
                        .map(provider -> Objects.requireNonNull(provider, "provider"))
                        .sorted(Comparator.comparing(Provider::providerId))
                        .toList();
        LinkedHashMap<String, Provider> indexed = new LinkedHashMap<>();
        for (Provider provider : ordered) {
            String providerId = requireProviderId(provider.providerId());
            if (indexed.putIfAbsent(providerId, provider) != null) {
                throw new IllegalArgumentException("Duplicate provider ID: " + providerId);
            }
        }
        return new ProviderRegistry(indexed);
    }

    public static ProviderRegistry empty() {
        return new ProviderRegistry(Map.of());
    }

    public Collection<Provider> providers() {
        return providers.values();
    }

    public ProviderHealthReport healthReport(Instant checkedAt) {
        Objects.requireNonNull(checkedAt, "checkedAt");
        List<ProviderHealthEntry> entries = new ArrayList<>();
        ProviderReadiness readiness = ProviderReadiness.READY;
        for (Provider provider : providers.values()) {
            ProviderHealth health = safeHealth(provider, checkedAt);
            entries.add(
                    new ProviderHealthEntry(
                            provider.providerId(),
                            provider.requirement(),
                            health.status(),
                            health.message(),
                            health.checkedAt()));
            if (provider.requirement() == ProviderRequirement.REQUIRED
                    && health.status() == ProviderStatus.UNAVAILABLE) {
                readiness = ProviderReadiness.MAINTENANCE;
            } else if (readiness == ProviderReadiness.READY
                    && health.status() != ProviderStatus.HEALTHY) {
                readiness = ProviderReadiness.DEGRADED;
            }
        }
        return new ProviderHealthReport(readiness, checkedAt, entries);
    }

    private static ProviderHealth safeHealth(Provider provider, Instant checkedAt) {
        try {
            return Objects.requireNonNull(provider.health(), "provider health");
        } catch (RuntimeException exception) {
            return ProviderHealth.unavailable(
                    "health check failed: " + exception.getClass().getSimpleName(), checkedAt);
        }
    }

    private static String requireProviderId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId is required");
        }
        return providerId;
    }
}
