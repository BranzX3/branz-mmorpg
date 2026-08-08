package com.branz.mmorpg.scenes.environment;

import com.branz.mmorpg.scenes.SceneSessionId;
import java.util.Objects;
import java.util.UUID;

/** Environment lease for a Scene; local Scenes retain their current world. */
public record SceneEnvironmentHandle(
        SceneSessionId sessionId, UUID viewerId, UUID worldId, String providerKey) {
    public SceneEnvironmentHandle {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(providerKey, "providerKey");
        if (providerKey.isBlank()) {
            throw new IllegalArgumentException("providerKey must not be blank");
        }
    }
}
