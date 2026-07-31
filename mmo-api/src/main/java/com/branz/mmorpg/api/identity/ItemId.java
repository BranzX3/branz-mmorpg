package com.branz.mmorpg.api.identity;

import java.util.Objects;
import java.util.UUID;

public record ItemId(UUID value) implements InstanceId {
    public ItemId {
        Objects.requireNonNull(value, "value");
    }
}
