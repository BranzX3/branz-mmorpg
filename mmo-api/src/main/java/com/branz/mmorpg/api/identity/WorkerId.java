package com.branz.mmorpg.api.identity;

import java.util.Objects;
import java.util.UUID;

public record WorkerId(UUID value) implements InstanceId {
    public WorkerId {
        Objects.requireNonNull(value, "value");
    }
}
