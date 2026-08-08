package com.branz.mmorpg.scenes.actor;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.scenes.SceneErrorCode;
import com.branz.mmorpg.scenes.SceneSession;

/** Creates, updates and destroys the owner-only world actor used by a Scene. */
public interface PreviewActorProvider {
    Result<PreviewActorHandle, SceneErrorCode> open(SceneSession session);

    Result<PreviewActorHandle, SceneErrorCode> update(
            PreviewActorHandle handle, SceneSession session);

    void close(PreviewActorHandle handle);
}
