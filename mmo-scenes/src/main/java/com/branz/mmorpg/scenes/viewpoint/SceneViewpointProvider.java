package com.branz.mmorpg.scenes.viewpoint;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.scenes.SceneErrorCode;
import com.branz.mmorpg.scenes.SceneSession;
import com.branz.mmorpg.scenes.actor.PreviewActorHandle;

/** Applies and safely restores the camera/viewpoint without owning world authority. */
public interface SceneViewpointProvider {
    Result<SceneViewpointHandle, SceneErrorCode> open(
            SceneSession session, PreviewActorHandle actor);

    void close(SceneViewpointHandle handle);
}
