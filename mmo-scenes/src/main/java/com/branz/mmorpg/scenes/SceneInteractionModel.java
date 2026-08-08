package com.branz.mmorpg.scenes;

/** Authority and input semantics for one mode inside a Scene presentation session. */
public enum SceneInteractionModel {
    READ_ONLY,
    PREVIEW_COMMIT,
    IMMEDIATE_ACTION,
    DIALOGUE,
    CINEMATIC
}
