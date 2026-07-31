package com.branz.mmorpg.api.identity;

import java.util.Objects;
import java.util.UUID;

/** One server login attempt/session identity; distinct from the durable character identity. */
public record SessionId(UUID value) {
    public SessionId {
        Objects.requireNonNull(value, "value");
    }
}
