package com.branz.mmorpg.api.player;

public enum PlayerSessionState {
    ABSENT,
    LOADING,
    ACTIVE,
    SAVING,
    CLOSED,
    LOAD_FAILED,
    SAVE_RETRY_PENDING,
    CONFLICTED
}
