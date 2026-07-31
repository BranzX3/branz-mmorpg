package com.branz.mmorpg.api.identity;

import java.util.Objects;
import java.util.UUID;

public record TransactionId(UUID value) implements InstanceId {
    public TransactionId {
        Objects.requireNonNull(value, "value");
    }
}
