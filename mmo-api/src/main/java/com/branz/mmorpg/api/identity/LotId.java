package com.branz.mmorpg.api.identity;

import java.util.Objects;
import java.util.UUID;

public record LotId(UUID value) implements InstanceId {
    public LotId {
        Objects.requireNonNull(value, "value");
    }
}
