package com.branz.mmorpg.core.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.skill.SkillDefinition;
import com.branz.mmorpg.api.social.TradeOffer;
import com.branz.mmorpg.api.social.TradeRepository;
import com.branz.mmorpg.api.social.TradeSnapshot;
import com.branz.mmorpg.api.social.TradeState;
import com.branz.mmorpg.core.fixture.FixedGameClock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultTradeServiceTest {
    private static final UUID A = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final ContentId ORE = ContentId.parse("branz:aether_ore");
    private static final ContentId BOUND = ContentId.parse("branz:bound_token");
    private final FixedGameClock clock =
            FixedGameClock.at("2026-07-26T00:00:00Z");

    @Test
    void offerMutationClearsBothConfirmations() {
        FakeTrades repository = new FakeTrades();
        DefaultTradeService service = new DefaultTradeService(
                repository, DefaultTradeServiceTest::snapshot, clock);
        TradeSnapshot trade = service.request(A, B);
        service.accept(trade.tradeId(), B);
        service.offer(trade.tradeId(), A, new TradeOffer(Map.of(ORE, 2L), Set.of()));
        service.confirm(trade.tradeId(), A);
        TradeSnapshot changed =
                service.offer(trade.tradeId(), B, new TradeOffer(Map.of(ORE, 1L), Set.of()));
        assertTrue(changed.confirmedPlayers().isEmpty());
        assertEquals(TradeState.OPEN, changed.state());
    }

    @Test
    void crashAfterAtomicCommitCannotDuplicateTransfer() {
        FakeTrades repository = new FakeTrades();
        repository.crashAfterCommit = true;
        DefaultTradeService service = new DefaultTradeService(
                repository, DefaultTradeServiceTest::snapshot, clock);
        TradeSnapshot trade = service.request(A, B);
        service.accept(trade.tradeId(), B);
        service.offer(trade.tradeId(), A, new TradeOffer(Map.of(ORE, 5L), Set.of()));
        service.confirm(trade.tradeId(), A);
        assertThrows(IllegalStateException.class,
                () -> service.confirm(trade.tradeId(), B));

        assertEquals(1, repository.transfers);
        assertEquals(TradeState.COMPLETE,
                service.trade(trade.tradeId()).orElseThrow().state());
        assertTrue(service.recover().isEmpty());
        assertEquals(1, repository.transfers);
    }

    @Test
    void nonTradableContentIsRejectedBeforeEscrow() {
        FakeTrades repository = new FakeTrades();
        DefaultTradeService service = new DefaultTradeService(
                repository, DefaultTradeServiceTest::snapshot, clock);
        TradeSnapshot trade = service.request(A, B);
        service.accept(trade.tradeId(), B);
        assertThrows(IllegalArgumentException.class, () -> service.offer(
                trade.tradeId(), A, new TradeOffer(Map.of(BOUND, 1L), Set.of())));
        assertTrue(service.trade(trade.tradeId()).orElseThrow()
                .offers().get(A).materials().isEmpty());
    }

    private static ContentSnapshot snapshot() {
        MaterialDefinition ore =
                new MaterialDefinition(ORE, "Ore", "ore", "common", true, 99);
        MaterialDefinition bound =
                new MaterialDefinition(BOUND, "Bound", "quest", "rare", false, 1);
        Map<ContentId, ContentDefinition> values = Map.of(ORE, ore, BOUND, bound);
        return new ContentSnapshot() {
            @Override public long revision() { return 1; }
            @Override public Instant loadedAt() { return Instant.EPOCH; }
            @Override public Collection<ContentDefinition> definitions() {
                return values.values();
            }
            @Override public Optional<ContentDefinition> find(ContentId id) {
                return Optional.ofNullable(values.get(id));
            }
            @Override public <T extends ContentDefinition> Optional<T> find(
                    ContentId id, Class<T> type) {
                return find(id).filter(type::isInstance).map(type::cast);
            }
            @Override public Map<ContentId, MaterialDefinition> materials() {
                return Map.of(ORE, ore, BOUND, bound);
            }
            @Override public Map<ContentId, SkillDefinition> skills() { return Map.of(); }
            @Override public Map<ContentId, LifeSkillDefinition> lifeSkills() { return Map.of(); }
            @Override public Map<ContentId, LifeSkillNodeDefinition> lifeSkillNodes() {
                return Map.of();
            }
            @Override public Map<ContentId, MasteryDefinition> masteries() { return Map.of(); }
            @Override public Map<ContentId, WeaponDefinition> weapons() { return Map.of(); }
        };
    }

    private static final class FakeTrades implements TradeRepository {
        private final Map<UUID, TradeSnapshot> values = new HashMap<>();
        private boolean crashAfterCommit;
        private int transfers;
        @Override public TradeSnapshot create(TradeSnapshot trade) {
            values.put(trade.tradeId(), trade);
            return trade;
        }
        @Override public Optional<TradeSnapshot> find(UUID tradeId) {
            return Optional.ofNullable(values.get(tradeId));
        }
        @Override public TradeSnapshot accept(UUID id, UUID recipient, Instant now) {
            return update(id, TradeState.OPEN, null, null);
        }
        @Override public TradeSnapshot replaceOffer(
                UUID id, UUID player, TradeOffer offer, Instant now) {
            TradeSnapshot before = values.get(id);
            HashMap<UUID, TradeOffer> offers = new HashMap<>(before.offers());
            offers.put(player, offer);
            return update(id, TradeState.OPEN, offers, Set.of());
        }
        @Override public TradeSnapshot confirm(UUID id, UUID player, Instant now) {
            TradeSnapshot before = values.get(id);
            HashSet<UUID> confirmations = new HashSet<>(before.confirmedPlayers());
            confirmations.add(player);
            return update(id, confirmations.size() == 2
                    ? TradeState.BOTH_CONFIRMED : TradeState.OPEN, null, confirmations);
        }
        @Override public TradeSnapshot commit(UUID id, Instant now) {
            TradeSnapshot before = values.get(id);
            if (before.state() == TradeState.COMPLETE) return before;
            transfers++;
            TradeSnapshot complete = update(id, TradeState.COMPLETE, null, null);
            if (crashAfterCommit) {
                crashAfterCommit = false;
                throw new IllegalStateException("injected crash after commit");
            }
            return complete;
        }
        @Override public TradeSnapshot cancel(
                UUID id, TradeState state, Instant now) {
            return update(id, state, Map.of(A, TradeOffer.empty(),
                    B, TradeOffer.empty()), Set.of());
        }
        @Override public Collection<TradeSnapshot> recoverable(Instant now) {
            return values.values().stream().filter(value ->
                    !Set.of(TradeState.COMPLETE, TradeState.CANCELLED, TradeState.EXPIRED)
                            .contains(value.state())).toList();
        }
        private TradeSnapshot update(
                UUID id, TradeState state, Map<UUID, TradeOffer> offers,
                Set<UUID> confirmations) {
            TradeSnapshot before = values.get(id);
            TradeSnapshot after = new TradeSnapshot(id, before.requesterId(),
                    before.recipientId(), state,
                    offers == null ? before.offers() : offers,
                    confirmations == null ? before.confirmedPlayers() : confirmations,
                    before.createdAt(), before.expiresAt(), before.revision() + 1);
            values.put(id, after);
            return after;
        }
    }
}
