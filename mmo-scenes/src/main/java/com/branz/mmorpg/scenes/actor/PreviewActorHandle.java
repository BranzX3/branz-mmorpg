package com.branz.mmorpg.scenes.actor;

import com.branz.mmorpg.scenes.SceneSessionId;
import java.util.Objects;
import java.util.UUID;

/** Opaque owner-only world actor owned by one Scene session. */
public record PreviewActorHandle(SceneSessionId sessionId, UUID viewerId, UUID actorId) {
    public PreviewActorHandle {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(actorId, "actorId");
    }
}
