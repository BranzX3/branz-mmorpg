package com.branz.mmorpg.api.player;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface PlayerProfileStore {
    CompletionStage<PlayerProfile> loadOrCreate(UUID playerId, String lastKnownName, Instant now);

    CompletionStage<PlayerProfile> save(PlayerProfile profile);
}
