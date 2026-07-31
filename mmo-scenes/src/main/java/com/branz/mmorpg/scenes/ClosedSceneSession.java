package com.branz.mmorpg.scenes;

import java.util.Objects;

public record ClosedSceneSession(SceneSession session, SceneCloseReason reason) {
    public ClosedSceneSession {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(reason, "reason");
    }
}
