package com.branz.mmorpg.api.identity;

import java.util.Objects;
import java.util.UUID;

public record LfgListingId(UUID value) implements InstanceId {
    public LfgListingId {
        Objects.requireNonNull(value, "value");
    }
}
