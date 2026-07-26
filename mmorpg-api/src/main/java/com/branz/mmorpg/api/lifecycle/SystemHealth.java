package com.branz.mmorpg.api.lifecycle;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SystemHealth(ServiceState state, Instant observedAt, List<ComponentHealth> components) {
    public SystemHealth {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(observedAt, "observedAt");
        components = List.copyOf(components);
    }

    public boolean ready() {
        return state == ServiceState.READY;
    }
}
