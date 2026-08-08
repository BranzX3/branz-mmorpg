package com.branz.mmorpg.scenes;

import com.branz.mmorpg.scenes.actor.PreviewActorHandle;
import com.branz.mmorpg.scenes.environment.SceneEnvironmentHandle;
import com.branz.mmorpg.scenes.overlay.MenuOverlayHandle;
import com.branz.mmorpg.scenes.viewpoint.SceneViewpointHandle;
import java.util.Objects;

/** Runtime provider handles acquired for one fully opened Scene. */
public record ScenePresentation(
        SceneEnvironmentHandle environment,
        PreviewActorHandle actor,
        SceneViewpointHandle viewpoint,
        MenuOverlayHandle overlay) {
    public ScenePresentation {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(viewpoint, "viewpoint");
        Objects.requireNonNull(overlay, "overlay");
        if (!environment.sessionId().equals(actor.sessionId())
                || !actor.sessionId().equals(viewpoint.sessionId())
                || !viewpoint.sessionId().equals(overlay.sessionId())) {
            throw new IllegalArgumentException(
                    "Scene presentation handles belong to different sessions");
        }
    }
}
