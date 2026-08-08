package com.branz.mmorpg.scenes;

/** Monotonic presentation lifecycle. Recovery can run from every non-terminal phase. */
public enum SceneLifecyclePhase {
    OPENING,
    ACTIVE,
    TRANSITIONING,
    COMMITTING,
    PLAYING,
    CLOSING,
    RECOVERING,
    CLOSED;

    public boolean terminal() {
        return this == CLOSED;
    }
}
