package com.branz.mmorpg.scenes.preview;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.scenes.SceneErrorCode;
import com.branz.mmorpg.scenes.SceneSession;

public interface ScenePreviewProvider {
    Result<ScenePreviewHandle, SceneErrorCode> open(SceneSession session);

    Result<ScenePreviewHandle, SceneErrorCode> update(
            ScenePreviewHandle handle, SceneSession session);

    void close(ScenePreviewHandle handle);
}
