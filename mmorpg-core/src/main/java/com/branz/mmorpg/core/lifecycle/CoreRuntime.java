package com.branz.mmorpg.core.lifecycle;

import com.branz.mmorpg.api.lifecycle.ComponentHealth;
import com.branz.mmorpg.api.lifecycle.HealthService;
import com.branz.mmorpg.api.lifecycle.ServiceState;
import com.branz.mmorpg.api.lifecycle.SystemHealth;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CoreRuntime implements HealthService, AutoCloseable {
    private final List<ManagedService> services;
    private final Clock clock;
    private final Map<String, ComponentHealth> components = new LinkedHashMap<>();
    private final List<ManagedService> startedServices = new ArrayList<>();
    private ServiceState state = ServiceState.NEW;

    public CoreRuntime(List<? extends ManagedService> services) {
        this(services, Clock.systemUTC());
    }

    public CoreRuntime(List<? extends ManagedService> services, Clock clock) {
        this.services = List.copyOf(services);
        this.clock = Objects.requireNonNull(clock, "clock");
        for (ManagedService service : this.services) {
            Objects.requireNonNull(service, "service");
            if (components.putIfAbsent(
                            service.name(),
                            new ComponentHealth(service.name(), ServiceState.NEW, service.required(), "not started"))
                    != null) {
                throw new IllegalArgumentException("Duplicate service name: " + service.name());
            }
        }
    }

    public synchronized void start() {
        if (state == ServiceState.READY) {
            return;
        }
        if (state != ServiceState.NEW && state != ServiceState.STOPPED) {
            throw new IllegalStateException("Cannot start Core runtime from " + state);
        }

        state = ServiceState.STARTING;
        startedServices.clear();
        for (ManagedService service : services) {
            update(service, ServiceState.STARTING, "starting");
            try {
                service.start();
                startedServices.add(service);
                update(service, ServiceState.READY, service.detail());
            } catch (Exception exception) {
                update(service, ServiceState.FAILED, message(exception));
                if (service.required()) {
                    rollbackStartedServices(exception);
                    state = ServiceState.FAILED;
                    throw new CoreLifecycleException(
                            "Required service failed to start: " + service.name(), exception);
                }
            }
        }
        state = ServiceState.READY;
    }

    public synchronized void stop() {
        if (state == ServiceState.NEW || state == ServiceState.STOPPED) {
            state = ServiceState.STOPPED;
            return;
        }
        if (state == ServiceState.STOPPING) {
            return;
        }

        state = ServiceState.STOPPING;
        Throwable firstFailure = null;
        for (int index = startedServices.size() - 1; index >= 0; index--) {
            ManagedService service = startedServices.get(index);
            update(service, ServiceState.STOPPING, "stopping");
            try {
                service.stop();
                update(service, ServiceState.STOPPED, service.detail());
            } catch (Exception exception) {
                update(service, ServiceState.FAILED, message(exception));
                if (firstFailure == null) {
                    firstFailure = exception;
                } else {
                    firstFailure.addSuppressed(exception);
                }
            }
        }
        startedServices.clear();
        state = firstFailure == null ? ServiceState.STOPPED : ServiceState.FAILED;
        if (firstFailure != null) {
            throw new CoreLifecycleException("One or more services failed to stop", firstFailure);
        }
    }

    @Override
    public synchronized SystemHealth health() {
        return new SystemHealth(state, clock.instant(), List.copyOf(components.values()));
    }

    @Override
    public void close() {
        stop();
    }

    private void rollbackStartedServices(Throwable startupFailure) {
        for (int index = startedServices.size() - 1; index >= 0; index--) {
            ManagedService service = startedServices.get(index);
            try {
                service.stop();
                update(service, ServiceState.STOPPED, "rolled back after startup failure");
            } catch (Exception stopFailure) {
                startupFailure.addSuppressed(stopFailure);
                update(service, ServiceState.FAILED, message(stopFailure));
            }
        }
        startedServices.clear();
    }

    private void update(ManagedService service, ServiceState nextState, String detail) {
        components.put(
                service.name(), new ComponentHealth(service.name(), nextState, service.required(), detail));
    }

    private static String message(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
