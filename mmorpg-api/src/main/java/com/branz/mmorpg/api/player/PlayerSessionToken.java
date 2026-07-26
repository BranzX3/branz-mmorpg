package com.branz.mmorpg.api.player;

public record PlayerSessionToken(long value) {
    public PlayerSessionToken {
        if (value <= 0) {
            throw new IllegalArgumentException("Session token must be positive");
        }
    }
}
