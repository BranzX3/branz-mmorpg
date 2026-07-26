package com.branz.mmorpg.api.service;

/**
 * A startable, stoppable core subsystem.
 *
 * <p>Implementations are started and stopped by a container in dependency
 * order. {@link #start()} either reaches {@link ServiceState#READY} or throws;
 * it never returns having partially initialised. {@link #stop()} is idempotent
 * and must release every executor, connection, and listener it created, so a
 * container can be started and stopped repeatedly within one JVM.
 */
public interface Service {

    /** Stable identifier used in status output and logs. */
    String name();

    ServiceState state();

    /**
     * Brings the service to {@link ServiceState#READY}.
     *
     * @throws com.branz.mmorpg.api.error.MMOException if the service cannot start
     */
    void start();

    /**
     * Releases everything the service owns. Safe to call from any state,
     * including {@link ServiceState#FAILED}, and safe to call twice.
     */
    void stop();

    /** Current status, including a human-readable detail when not READY. */
    default ServiceStatus status() {
        return new ServiceStatus(name(), state(), null);
    }
}
