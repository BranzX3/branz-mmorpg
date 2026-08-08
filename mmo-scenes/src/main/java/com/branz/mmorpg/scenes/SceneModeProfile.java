package com.branz.mmorpg.scenes;

import java.util.Objects;

/** Immutable behavior contract for one mode within a SceneProfile. */
public record SceneModeProfile(
        SceneMode mode,
        SceneInteractionModel interactionModel,
        boolean restContextRequiredForConfirm) {
    public SceneModeProfile {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(interactionModel, "interactionModel");
        if (restContextRequiredForConfirm
                && interactionModel != SceneInteractionModel.PREVIEW_COMMIT) {
            throw new IllegalArgumentException(
                    "Rest-locked confirmation requires PREVIEW_COMMIT semantics");
        }
    }

    public static SceneModeProfile readOnly(SceneMode mode) {
        return new SceneModeProfile(mode, SceneInteractionModel.READ_ONLY, false);
    }

    public static SceneModeProfile previewCommit(SceneMode mode, boolean restLocked) {
        return new SceneModeProfile(mode, SceneInteractionModel.PREVIEW_COMMIT, restLocked);
    }

    public static SceneModeProfile immediate(SceneMode mode) {
        return new SceneModeProfile(mode, SceneInteractionModel.IMMEDIATE_ACTION, false);
    }
}
