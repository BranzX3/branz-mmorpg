package com.branz.mmorpg.core.player;

/** Bounded persistence retry policy. Delayed retries can be added through the scheduler port. */
public record PlayerSessionSavePolicy(int maximumAttempts) {
    public static final PlayerSessionSavePolicy DEFAULT = new PlayerSessionSavePolicy(3);

    public PlayerSessionSavePolicy {
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException("Maximum save attempts must be positive");
        }
    }
}
