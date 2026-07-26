package com.branz.mmorpg.core.social;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.item.ItemCategory;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.api.social.TradeOffer;
import com.branz.mmorpg.api.social.TradeRepository;
import com.branz.mmorpg.api.social.TradeService;
import com.branz.mmorpg.api.social.TradeSnapshot;
import com.branz.mmorpg.api.social.TradeState;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class DefaultTradeService implements TradeService {
    private static final Duration TIMEOUT = Duration.ofMinutes(2);
    private final TradeRepository repository;
    private final Supplier<ContentSnapshot> content;
    private final GameClock clock;

    public DefaultTradeService(TradeRepository repository,
                               Supplier<ContentSnapshot> content, GameClock clock) {
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.content = java.util.Objects.requireNonNull(content, "content");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    @Override public TradeSnapshot request(UUID requesterId, UUID recipientId) {
        if (requesterId.equals(recipientId)) {
            throw new IllegalArgumentException("cannot trade with self");
        }
        return repository.create(new TradeSnapshot(UUID.randomUUID(), requesterId, recipientId,
                TradeState.REQUESTED, Map.of(requesterId, TradeOffer.empty(),
                        recipientId, TradeOffer.empty()), Set.of(),
                clock.now(), clock.now().plus(TIMEOUT), 0));
    }

    @Override public TradeSnapshot accept(UUID tradeId, UUID recipientId) {
        return repository.accept(tradeId, recipientId, clock.now());
    }

    @Override public TradeSnapshot offer(
            UUID tradeId, UUID playerId, TradeOffer offer) {
        validateContent(offer);
        return repository.replaceOffer(tradeId, playerId, offer, clock.now());
    }

    @Override public TradeSnapshot confirm(UUID tradeId, UUID playerId) {
        TradeSnapshot confirmed = repository.confirm(tradeId, playerId, clock.now());
        if (confirmed.state() == TradeState.BOTH_CONFIRMED) {
            return repository.commit(tradeId, clock.now());
        }
        return confirmed;
    }

    @Override public TradeSnapshot cancel(UUID tradeId, UUID playerId) {
        TradeSnapshot trade = repository.find(tradeId).orElseThrow(
                () -> new IllegalArgumentException("unknown trade " + tradeId));
        trade.counterpart(playerId);
        return repository.cancel(tradeId, TradeState.CANCELLED, clock.now());
    }

    @Override public void logout(UUID playerId) {
        repository.recoverable(clock.now()).stream()
                .filter(trade -> trade.requesterId().equals(playerId)
                        || trade.recipientId().equals(playerId))
                .forEach(trade -> repository.cancel(
                        trade.tradeId(), TradeState.CANCELLED, clock.now()));
    }

    @Override public Optional<TradeSnapshot> trade(UUID tradeId) {
        return repository.find(tradeId);
    }

    @Override public Collection<TradeSnapshot> recover() {
        for (TradeSnapshot trade : repository.recoverable(clock.now())) {
            if (!trade.expiresAt().isAfter(clock.now())) {
                repository.cancel(trade.tradeId(), TradeState.EXPIRED, clock.now());
            } else if (trade.state() == TradeState.BOTH_CONFIRMED
                    || trade.state() == TradeState.COMMITTING) {
                repository.commit(trade.tradeId(), clock.now());
            }
        }
        return repository.recoverable(clock.now());
    }

    private void validateContent(TradeOffer offer) {
        ContentSnapshot snapshot = content.get();
        offer.materials().keySet().forEach(id -> {
            ContentDefinition definition = snapshot.find(id).orElseThrow(
                    () -> new IllegalArgumentException("unknown trade material " + id));
            if (!(definition instanceof MaterialDefinition material) || !material.tradable()) {
                throw new IllegalArgumentException("material is not tradable " + id);
            }
        });
        // Unique-item ownership, binding, quest category, equipment, and escrow locking
        // are validated atomically by the repository against the authoritative rows.
    }
}
