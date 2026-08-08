package com.branz.mmorpg.scenes.overlay;

import com.branz.mmorpg.scenes.SceneSessionId;
import java.util.Objects;
import java.util.UUID;

/** Control overlay handle; the overlay never owns the Scene actor or environment. */
public record MenuOverlayHandle(SceneSessionId sessionId, UUID viewerId, String rendererKey) {
    public MenuOverlayHandle {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(rendererKey, "rendererKey");
        if (rendererKey.isBlank()) {
            throw new IllegalArgumentException("rendererKey must not be blank");
        }
    }
}
