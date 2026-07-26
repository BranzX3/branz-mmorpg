package com.branz.mmorpg.api.lifecycle;

import java.util.Objects;

public record ComponentHealth(String name, ServiceState state, boolean required, String detail) {
    public ComponentHealth {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(state, "state");
        detail = Objects.requireNonNullElse(detail, "");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Component name must not be blank");
        }
    }
}
