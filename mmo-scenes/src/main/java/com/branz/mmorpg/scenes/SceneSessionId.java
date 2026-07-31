package com.branz.mmorpg.scenes;

import java.util.Objects;
import java.util.UUID;

public record SceneSessionId(UUID value) {
    public SceneSessionId {
        Objects.requireNonNull(value, "value");
    }

    public static SceneSessionId random() {
        return new SceneSessionId(UUID.randomUUID());
    }
}
