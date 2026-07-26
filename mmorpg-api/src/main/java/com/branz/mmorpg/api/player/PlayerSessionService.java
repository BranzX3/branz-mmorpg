package com.branz.mmorpg.api.player;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;

public interface PlayerSessionService {
    CompletionStage<PlayerSessionSnapshot> open(
            UUID playerId, String lastKnownName, long contentRevision);

    CompletionStage<PlayerSessionSnapshot> close(UUID playerId, PlayerSessionToken token);

    /**
     * Applies an in-memory profile mutation to the authoritative ACTIVE session.
     * Persistence is performed later by {@link #save(UUID, PlayerSessionToken)} or close.
     */
    default PlayerSessionSnapshot updateProfile(
            UUID playerId,
            PlayerSessionToken token,
            PlayerProfileComponent component,
            UnaryOperator<PlayerProfile> update) {
        return updateProfile(playerId, token, Set.of(component), update);
    }

    PlayerSessionSnapshot updateProfile(
            UUID playerId,
            PlayerSessionToken token,
            Set<PlayerProfileComponent> components,
            UnaryOperator<PlayerProfile> update);

    /** Saves the current dirty profile while keeping the session active. */
    CompletionStage<PlayerSessionSnapshot> save(UUID playerId, PlayerSessionToken token);

    Optional<PlayerSessionSnapshot> snapshot(UUID playerId);

    int activeSessionCount();

    int dirtySessionCount();
}
