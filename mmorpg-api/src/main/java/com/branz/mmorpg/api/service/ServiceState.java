package com.branz.mmorpg.api.service;

/**
 * Lifecycle of a core service.
 *
 * <p>The legal transitions are:
 *
 * <pre>
 * NEW -> STARTING -> READY -> STOPPING -> STOPPED
 *         |            |         |
 *         +------------+---------+--> FAILED
 * </pre>
 *
 * <p>{@link #FAILED} is terminal for the owning container: a service that failed
 * is never silently restarted, because a half-initialised service is
 * indistinguishable from a working one to its callers.
 */
public enum ServiceState {
    NEW,
    STARTING,
    READY,
    STOPPING,
    STOPPED,
    FAILED;

    /** Whether this service is usable by gameplay code. */
    public boolean operational() {
        return this == READY;
    }

    /** Whether the lifecycle has finished, successfully or not. */
    public boolean terminal() {
        return this == STOPPED || this == FAILED;
    }

    /** Whether {@code target} may follow this state. */
    public boolean canTransitionTo(ServiceState target) {
        if (target == null || target == NEW) {
            return false;
        }
        if (target == FAILED) {
            return this == STARTING || this == READY || this == STOPPING;
        }
        return switch (this) {
            case NEW -> target == STARTING;
            case STARTING -> target == READY;
            case READY -> target == STOPPING;
            case STOPPING -> target == STOPPED;
            case STOPPED, FAILED -> false;
        };
    }
}
