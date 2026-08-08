package com.branz.mmorpg.scenes.environment;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.scenes.SceneErrorCode;
import com.branz.mmorpg.scenes.SceneSession;

/** Acquires and releases the world/environment presentation for a Scene. */
public interface SceneEnvironmentProvider {
    Result<SceneEnvironmentHandle, SceneErrorCode> open(SceneSession session);

    void close(SceneEnvironmentHandle handle);
}
