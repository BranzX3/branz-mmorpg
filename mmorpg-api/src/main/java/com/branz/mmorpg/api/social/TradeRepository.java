package com.branz.mmorpg.api.social;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Blocking atomic escrow port. Currency is deliberately not stored here. */
public interface TradeRepository {
    TradeSnapshot create(TradeSnapshot trade);
    Optional<TradeSnapshot> find(UUID tradeId);
    TradeSnapshot accept(UUID tradeId, UUID recipientId, Instant now);
    TradeSnapshot replaceOffer(UUID tradeId, UUID playerId, TradeOffer offer, Instant now);
    TradeSnapshot confirm(UUID tradeId, UUID playerId, Instant now);
    TradeSnapshot commit(UUID tradeId, Instant now);
    TradeSnapshot cancel(UUID tradeId, TradeState terminalState, Instant now);
    Collection<TradeSnapshot> recoverable(Instant now);
}
