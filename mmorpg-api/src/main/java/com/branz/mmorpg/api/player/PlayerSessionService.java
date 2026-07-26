package com.branz.mmorpg.api.player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface PlayerSessionService {
    CompletionStage<PlayerSessionSnapshot> open(
            UUID playerId, String lastKnownName, long contentRevision);

    CompletionStage<PlayerSessionSnapshot> close(UUID playerId, PlayerSessionToken token);

    Optional<PlayerSessionSnapshot> snapshot(UUID playerId);

    int activeSessionCount();
}
