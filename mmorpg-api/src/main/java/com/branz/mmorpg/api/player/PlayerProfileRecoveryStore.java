package com.branz.mmorpg.api.player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface PlayerProfileRecoveryStore {
    CompletionStage<Optional<PlayerProfileRecoveryRecord>> load(UUID playerId);

    CompletionStage<Void> write(PlayerProfileRecoveryRecord record);

    CompletionStage<Void> delete(UUID playerId);

    static PlayerProfileRecoveryStore none() {
        return new PlayerProfileRecoveryStore() {
            @Override
            public CompletionStage<Optional<PlayerProfileRecoveryRecord>> load(UUID playerId) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            @Override
            public CompletionStage<Void> write(PlayerProfileRecoveryRecord record) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("No durable player profile recovery store is configured"));
            }

            @Override
            public CompletionStage<Void> delete(UUID playerId) {
                return CompletableFuture.completedFuture(null);
            }
        };
    }
}
