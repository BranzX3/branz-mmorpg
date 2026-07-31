package com.branz.mmorpg.api.identity;

import java.util.Objects;
import java.util.UUID;

public record EncounterId(UUID value) implements InstanceId {
    public EncounterId {
        Objects.requireNonNull(value, "value");
    }
}
