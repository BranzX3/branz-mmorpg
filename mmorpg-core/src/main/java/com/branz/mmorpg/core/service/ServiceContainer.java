package com.branz.mmorpg.core.service;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.service.HealthReport;
import com.branz.mmorpg.api.service.Service;
import com.branz.mmorpg.api.service.ServiceState;
import com.branz.mmorpg.api.service.ServiceStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Starts services in registration order and stops them in reverse.
 *
 * <p>Registration order <em>is</em> dependency order: a service may only be
 * registered after everything it needs. There is no dependency graph to resolve,
 * because an explicit list is easier to reason about than an inferred one and
 * the set is small.
 *
 * <p>Start is all-or-nothing. If any service fails, the ones already started are
 * stopped in reverse and the container never reports {@link #ready()}. A partly
 * started core is never handed to gameplay code.
 */
public final class ServiceContainer implements AutoCloseable {

    private final List<Service> services = new ArrayList<>();
    private final List<Service> started = new ArrayList<>();
    private boolean startAttempted;

    /** @return the service, for convenient assignment at the registration site */
    public <T extends Service> T register(T service) {
        Objects.requireNonNull(service, "service");
        if (startAttempted) {
            throw new MMOException(ErrorCode.SERVICE_LIFECYCLE,
                    "cannot register " + service.name() + " after start");
        }
        if (services.stream().anyMatch(existing -> existing.name().equals(service.name()))) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "duplicate service name: " + service.name());
        }
        services.add(service);
        return service;
    }

    /**
     * Starts every registered service.
     *
     * @throws MMOException if any service fails; already-started services are
     *         stopped first
     */
    public void startAll() {
        if (startAttempted) {
            throw new MMOException(ErrorCode.SERVICE_LIFECYCLE, "container already started");
        }
        startAttempted = true;
        for (Service service : services) {
            try {
                service.start();
                started.add(service);
            } catch (RuntimeException exception) {
                stopStarted();
                throw exception instanceof MMOException mmo
                        ? mmo
                        : new MMOException(ErrorCode.SERVICE_UNAVAILABLE,
                                "service " + service.name() + " failed to start", exception);
            }
        }
    }

    /** True only when every registered service is READY. */
    public boolean ready() {
        return !services.isEmpty()
                && services.stream().map(Service::state).allMatch(ServiceState::operational);
    }

    public HealthReport health() {
        return HealthReport.of(services.stream().map(Service::status).toList());
    }

    public ServiceStatus status(String name) {
        return services.stream()
                .filter(service -> service.name().equals(name))
                .map(Service::status)
                .findFirst()
                .orElseThrow(() -> new MMOException(ErrorCode.INVALID_ARGUMENT, "no such service: " + name));
    }

    /**
     * Stops everything that started, in reverse order. Idempotent, and a
     * throwing {@code stop} never prevents the remaining services from stopping.
     */
    public void stopAll() {
        stopStarted();
    }

    @Override
    public void close() {
        stopAll();
    }

    private void stopStarted() {
        List<RuntimeException> failures = new ArrayList<>();
        for (int index = started.size() - 1; index >= 0; index--) {
            try {
                started.get(index).stop();
            } catch (RuntimeException exception) {
                failures.add(exception);
            }
        }
        started.clear();
        if (!failures.isEmpty()) {
            MMOException aggregate = new MMOException(ErrorCode.SERVICE_LIFECYCLE,
                    failures.size() + " service(s) failed to stop cleanly", failures.getFirst());
            failures.stream().skip(1).forEach(aggregate::addSuppressed);
            throw aggregate;
        }
    }
}
