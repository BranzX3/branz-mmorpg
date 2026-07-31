package com.branz.mmorpg.scenes.preview;

import com.branz.mmorpg.scenes.SceneSessionId;
import java.util.Objects;
import java.util.UUID;

public record ScenePreviewHandle(SceneSessionId sessionId, UUID viewerId, ScenePreviewMode mode) {
    public ScenePreviewHandle {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(mode, "mode");
    }
}
