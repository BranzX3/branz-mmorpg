package com.branz.mmorpg.scenes;

import com.branz.mmorpg.api.result.ErrorCode;

public enum SceneErrorCode implements ErrorCode {
    SCENE_ALREADY_OPEN,
    SCENE_NOT_FOUND,
    SCENE_STALE_SESSION,
    SCENE_NOT_ELIGIBLE,
    SCENE_PREVIEW_UNAVAILABLE,
    SCENE_COMMIT_REJECTED,
    SCENE_MODE_UNAVAILABLE,
    SCENE_INTERACTION_MODEL_MISMATCH,
    SCENE_PROVIDER_FAILURE;

    @Override
    public String code() {
        return name();
    }
}
