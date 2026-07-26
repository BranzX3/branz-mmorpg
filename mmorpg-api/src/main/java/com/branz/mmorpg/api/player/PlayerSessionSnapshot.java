package com.branz.mmorpg.api.player;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PlayerSessionSnapshot(
        UUID playerId,
        PlayerSessionToken token,
        PlayerSessionState state,
        Optional<PlayerProfile> profile,
        long contentRevision,
        String detail) {

    public PlayerSessionSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(state, "state");
        profile = Objects.requireNonNull(profile, "profile");
        detail = Objects.requireNonNullElse(detail, "");
        if (contentRevision < 0) {
            throw new IllegalArgumentException("Content revision must not be negative");
        }
        if (state == PlayerSessionState.ACTIVE && profile.isEmpty()) {
            throw new IllegalArgumentException("An ACTIVE session requires a profile");
        }
    }
}
