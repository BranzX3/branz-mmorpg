package com.branz.mmorpg.api.identity;

import java.util.Objects;
import java.util.UUID;

public record MountId(UUID value) implements InstanceId {
    public MountId {
        Objects.requireNonNull(value, "value");
    }
}
