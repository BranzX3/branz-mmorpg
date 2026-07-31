package com.branz.mmorpg.api.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderRegistryTest {
    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void reportsReadyWhenEveryProviderIsHealthy() {
        ProviderRegistry registry =
                ProviderRegistry.of(
                        List.of(
                                provider(
                                        "wallet",
                                        ProviderRequirement.REQUIRED,
                                        ProviderHealth.healthy(NOW)),
                                provider(
                                        "packets",
                                        ProviderRequirement.OPTIONAL,
                                        ProviderHealth.healthy(NOW))));

        ProviderHealthReport report = registry.healthReport(NOW);

        assertEquals(ProviderReadiness.READY, report.readiness());
        assertTrue(report.acceptsSessions());
        assertEquals(List.of("packets", "wallet"), providerIds(report));
    }

    @Test
    void optionalFailureDegradesButRequiredFailureEntersMaintenance() {
        ProviderRegistry optionalFailure =
                ProviderRegistry.of(
                        List.of(
                                provider(
                                        "packets",
                                        ProviderRequirement.OPTIONAL,
                                        ProviderHealth.unavailable("missing", NOW))));
        ProviderRegistry requiredFailure =
                ProviderRegistry.of(
                        List.of(
                                provider(
                                        "wallet",
                                        ProviderRequirement.REQUIRED,
                                        ProviderHealth.unavailable("missing", NOW))));

        assertEquals(ProviderReadiness.DEGRADED, optionalFailure.healthReport(NOW).readiness());
        ProviderHealthReport maintenance = requiredFailure.healthReport(NOW);
        assertEquals(ProviderReadiness.MAINTENANCE, maintenance.readiness());
        assertFalse(maintenance.acceptsSessions());
    }

    @Test
    void healthExceptionFailsSafeAndDuplicateIdsAreRejected() {
        Provider throwing =
                new Provider() {
                    @Override
                    public String providerId() {
                        return "wallet";
                    }

                    @Override
                    public ProviderRequirement requirement() {
                        return ProviderRequirement.REQUIRED;
                    }

                    @Override
                    public ProviderHealth health() {
                        throw new IllegalStateException("provider secret");
                    }
                };

        ProviderHealthReport report = ProviderRegistry.of(List.of(throwing)).healthReport(NOW);

        assertEquals(ProviderReadiness.MAINTENANCE, report.readiness());
        assertEquals(
                "health check failed: IllegalStateException",
                report.providers().getFirst().message());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ProviderRegistry.of(
                                List.of(
                                        provider(
                                                "same",
                                                ProviderRequirement.OPTIONAL,
                                                ProviderHealth.healthy(NOW)),
                                        provider(
                                                "same",
                                                ProviderRequirement.REQUIRED,
                                                ProviderHealth.healthy(NOW)))));
    }

    private static Provider provider(
            String id, ProviderRequirement requirement, ProviderHealth health) {
        return new Provider() {
            @Override
            public String providerId() {
                return id;
            }

            @Override
            public ProviderRequirement requirement() {
                return requirement;
            }

            @Override
            public ProviderHealth health() {
                return health;
            }
        };
    }

    private static List<String> providerIds(ProviderHealthReport report) {
        return report.providers().stream().map(ProviderHealthEntry::providerId).toList();
    }
}
