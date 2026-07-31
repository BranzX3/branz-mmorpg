package com.branz.mmorpg.api.identity;

import java.util.Objects;
import java.util.UUID;

public record CharacterId(UUID value) implements InstanceId {
    public CharacterId {
        Objects.requireNonNull(value, "value");
    }
}
