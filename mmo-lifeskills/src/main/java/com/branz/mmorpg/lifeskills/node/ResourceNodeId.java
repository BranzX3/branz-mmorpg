package com.branz.mmorpg.lifeskills.node;

import java.util.Objects;
import java.util.UUID;

public record ResourceNodeId(UUID value) {
    public ResourceNodeId {
        Objects.requireNonNull(value, "value");
    }
}
