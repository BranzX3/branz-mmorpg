package com.branz.mmorpg.api.social;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record TradeSnapshot(
        UUID tradeId,
        UUID requesterId,
        UUID recipientId,
        TradeState state,
        Map<UUID, TradeOffer> offers,
        Set<UUID> confirmedPlayers,
        Instant createdAt,
        Instant expiresAt,
        long revision) {
    public TradeSnapshot {
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(state, "state");
        offers = Map.copyOf(offers);
        confirmedPlayers = Set.copyOf(confirmedPlayers);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (requesterId.equals(recipientId) || !expiresAt.isAfter(createdAt) || revision < 0
                || !Set.of(requesterId, recipientId).containsAll(offers.keySet())
                || !Set.of(requesterId, recipientId).containsAll(confirmedPlayers)) {
            throw new IllegalArgumentException("invalid trade snapshot");
        }
    }

    public UUID counterpart(UUID playerId) {
        if (requesterId.equals(playerId)) return recipientId;
        if (recipientId.equals(playerId)) return requesterId;
        throw new IllegalArgumentException("player is not a trade participant");
    }
}
