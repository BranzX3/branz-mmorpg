package com.branz.mmorpg.api.identity;

import java.util.Objects;
import java.util.UUID;

public record PartyId(UUID value) implements InstanceId {
    public PartyId {
        Objects.requireNonNull(value, "value");
    }
}
