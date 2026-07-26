package com.branz.mmorpg.api.operation;

import java.util.Objects;
import java.util.UUID;

public record EventId(UUID value) {
    public EventId {
        Objects.requireNonNull(value, "value");
    }

    public static EventId parse(String value) {
        return new EventId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
