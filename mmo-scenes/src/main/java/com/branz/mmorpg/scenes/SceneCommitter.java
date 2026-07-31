package com.branz.mmorpg.scenes;

import com.branz.mmorpg.api.result.Result;

@FunctionalInterface
public interface SceneCommitter {
    Result<ScenePreviewState, SceneErrorCode> commit(SceneSession session);
}
