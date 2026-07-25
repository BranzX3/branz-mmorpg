package com.branz.mmorpg.api.service;

import java.util.Objects;

/**
 * Immutable status of one service.
 *
 * @param name   service identifier
 * @param state  lifecycle state
 * @param detail failure or progress detail, null when uninteresting
 */
public record ServiceStatus(String name, ServiceState state, String detail) {

    public ServiceStatus {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(state, "state");
    }

    public boolean operational() {
        return state.operational();
    }
}
