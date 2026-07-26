package com.branz.mmorpg.api.service;

import java.util.List;
import java.util.Objects;

/**
 * Aggregate health of the core service container, as rendered by
 * {@code /branz status}.
 *
 * <p>{@link #ready()} is true only when every registered service is
 * {@link ServiceState#READY}. A single failed required service therefore keeps
 * the whole core out of READY, which is what gameplay code checks before
 * performing a mutation.
 */
public record HealthReport(boolean ready, List<ServiceStatus> services) {

    public HealthReport {
        Objects.requireNonNull(services, "services");
        services = List.copyOf(services);
    }

    public static HealthReport of(List<ServiceStatus> services) {
        Objects.requireNonNull(services, "services");
        boolean ready = !services.isEmpty() && services.stream().allMatch(ServiceStatus::operational);
        return new HealthReport(ready, services);
    }

    /** Services that are not operational, in registration order. */
    public List<ServiceStatus> degraded() {
        return services.stream().filter(status -> !status.operational()).toList();
    }
}
