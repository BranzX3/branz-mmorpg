package com.branz.mmorpg.core.service;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.service.Service;
import com.branz.mmorpg.api.service.ServiceState;
import com.branz.mmorpg.api.service.ServiceStatus;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class enforcing the {@link ServiceState} machine so subclasses only
 * write {@link #onStart()} and {@link #onStop()}.
 *
 * <p>A throwing {@code onStart} leaves the service {@link ServiceState#FAILED}
 * and calls {@code onStop} so partially acquired resources are released. That is
 * what makes repeated start/stop in tests leak-free.
 */
public abstract class AbstractService implements Service {

    private final String name;
    private final AtomicReference<ServiceState> state = new AtomicReference<>(ServiceState.NEW);
    private volatile String detail;

    protected AbstractService(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final ServiceState state() {
        return state.get();
    }

    @Override
    public final ServiceStatus status() {
        return new ServiceStatus(name, state.get(), detail);
    }

    @Override
    public final void start() {
        transition(ServiceState.STARTING);
        try {
            onStart();
        } catch (RuntimeException exception) {
            fail(exception.getMessage());
            safeStop();
            throw exception instanceof MMOException mmo
                    ? mmo
                    : new MMOException(ErrorCode.SERVICE_UNAVAILABLE, name + " failed to start", exception);
        }
        transition(ServiceState.READY);
        detail = null;
    }

    @Override
    public final void stop() {
        ServiceState current = state.get();
        if (current == ServiceState.NEW || current.terminal()) {
            // Nothing was acquired, or the lifecycle is already over.
            return;
        }
        transition(ServiceState.STOPPING);
        safeStop();
        state.set(ServiceState.STOPPED);
    }

    /** Acquires resources. Throwing here fails the whole container. */
    protected abstract void onStart();

    /** Releases resources. Must tolerate a partially completed {@link #onStart()}. */
    protected abstract void onStop();

    /** Records a non-fatal note shown by {@code /branz status}. */
    protected final void detail(String message) {
        this.detail = message;
    }

    private void safeStop() {
        try {
            onStop();
        } catch (RuntimeException suppressed) {
            detail((detail == null ? "" : detail + "; ") + "stop failed: " + suppressed.getMessage());
        }
    }

    private void fail(String message) {
        state.set(ServiceState.FAILED);
        detail(message == null ? "start failed" : message);
    }

    private void transition(ServiceState target) {
        ServiceState previous = state.getAndUpdate(current -> current.canTransitionTo(target) ? target : current);
        if (!previous.canTransitionTo(target)) {
            throw new MMOException(ErrorCode.SERVICE_LIFECYCLE,
                    name + " cannot go from " + previous + " to " + target);
        }
    }
}
