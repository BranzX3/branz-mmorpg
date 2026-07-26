package com.branz.mmorpg.api.player;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record PlayerSessionSnapshot(
        UUID playerId,
        PlayerSessionToken token,
        PlayerSessionState state,
        Optional<PlayerProfile> profile,
        Set<PlayerProfileComponent> dirtyComponents,
        long contentRevision,
        String detail) {

    public PlayerSessionSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(state, "state");
        profile = Objects.requireNonNull(profile, "profile");
        dirtyComponents = Set.copyOf(dirtyComponents);
        detail = Objects.requireNonNullElse(detail, "");
        if (contentRevision < 0) {
            throw new IllegalArgumentException("Content revision must not be negative");
        }
        if (state == PlayerSessionState.ACTIVE && profile.isEmpty()) {
            throw new IllegalArgumentException("An ACTIVE session requires a profile");
        }
        if (profile.isEmpty() && !dirtyComponents.isEmpty()) {
            throw new IllegalArgumentException("A session without a profile cannot be dirty");
        }
    }
}
