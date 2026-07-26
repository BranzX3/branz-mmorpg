package com.branz.mmorpg.api.operation;

import java.util.Objects;
import java.util.UUID;

public record OperationId(UUID value) {
    public OperationId {
        Objects.requireNonNull(value, "value");
    }

    public static OperationId parse(String value) {
        return new OperationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
