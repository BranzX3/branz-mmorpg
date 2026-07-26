package com.branz.mmorpg.api.social;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface TradeService {
    TradeSnapshot request(UUID requesterId, UUID recipientId);
    TradeSnapshot accept(UUID tradeId, UUID recipientId);
    TradeSnapshot offer(UUID tradeId, UUID playerId, TradeOffer offer);
    TradeSnapshot confirm(UUID tradeId, UUID playerId);
    TradeSnapshot cancel(UUID tradeId, UUID playerId);
    void logout(UUID playerId);
    Optional<TradeSnapshot> trade(UUID tradeId);
    Collection<TradeSnapshot> recover();
}
