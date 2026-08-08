package com.branz.mmorpg.scenes.viewpoint;

import com.branz.mmorpg.scenes.SceneSessionId;
import java.util.Objects;
import java.util.UUID;

/** Viewpoint ownership plus the baseline required for deterministic restoration. */
public record SceneViewpointHandle(
        SceneSessionId sessionId,
        UUID viewerId,
        UUID actorId,
        UUID worldId,
        float originalYaw,
        float originalPitch) {
    public SceneViewpointHandle {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(worldId, "worldId");
    }
}
