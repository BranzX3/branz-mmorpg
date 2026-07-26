package com.branz.mmorpg.core.mastery;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.mastery.CombatMasteryRepository;
import com.branz.mmorpg.api.mastery.CombatMasteryService;
import com.branz.mmorpg.api.mastery.MasteryMutationCommit;
import com.branz.mmorpg.api.mastery.MasterySnapshot;
import com.branz.mmorpg.api.mastery.CombatMasteryLevelChanged;
import com.branz.mmorpg.api.mastery.CombatMasteryNodeUnlocked;
import com.branz.mmorpg.api.event.EventBus;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.runtime.GameClock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultCombatMasteryService implements CombatMasteryService {

    private final CombatMasteryRepository repository;
    private final Supplier<ContentSnapshot> content;
    private final GameClock clock;
    private final EventBus events;
    private final ConcurrentHashMap<UUID, Map<ContentId, MasterySnapshot>> active =
            new ConcurrentHashMap<>();
    private final Object[] mutationLocks = locks();
    private volatile Consumer<MasteryChanged> listener = ignored -> {};

    public record MasteryChanged(
            UUID playerId, ContentId masteryId, MasteryMutationCommit commit,
            OperationId operationId) {
    }

    public void mutationListener(Consumer<MasteryChanged> listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public DefaultCombatMasteryService(CombatMasteryRepository repository,
                                       Supplier<ContentSnapshot> content,
                                       GameClock clock) {
        this(repository, content, clock, new com.branz.mmorpg.core.event.SimpleEventBus());
    }

    public DefaultCombatMasteryService(CombatMasteryRepository repository,
                                       Supplier<ContentSnapshot> content,
                                       GameClock clock, EventBus events) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.content = Objects.requireNonNull(content, "content");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    public Map<ContentId, MasterySnapshot> profile(UUID playerId) {
        return active.computeIfAbsent(playerId,
                ignored -> Map.copyOf(repository.load(playerId)));
    }

    @Override
    public MasteryMutationCommit grantContribution(
            UUID playerId, ContentId masteryId, long baseXp,
            double antiFarmMultiplier, OperationId operationId) {
        var definition = content.get().masteries().get(masteryId);
        if (definition == null) {
            throw new IllegalArgumentException("unknown combat mastery " + masteryId);
        }
        CombatMasteryEngine engine = new CombatMasteryEngine(definition, content.get().masteryNodes());
        long awarded = engine.awardAmount(baseXp, antiFarmMultiplier);
        MasteryMutationCommit commit;
        synchronized (lock(playerId)) {
            commit = repository.mutate(playerId, masteryId, operationId, awarded,
                    current -> engine.award(current, awarded, clock.now()));
            updateCache(playerId, masteryId, commit.after());
        }
        if (commit.applied()) {
            publishLevelChange(playerId, masteryId, commit);
            try {
                listener.accept(new MasteryChanged(
                        playerId, masteryId, commit, operationId));
            } catch (RuntimeException ignored) {
                // Mastery commit remains authoritative if an observer is offline.
            }
        }
        return commit;
    }

    @Override
    public MasteryMutationCommit purchaseNode(UUID playerId, ContentId masteryId,
                                              ContentId nodeId, OperationId operationId) {
        var definition = requireDefinition(masteryId);
        CombatMasteryEngine engine = new CombatMasteryEngine(definition, content.get().masteryNodes());
        MasteryMutationCommit commit;
        synchronized (lock(playerId)) {
            commit = repository.mutate(playerId, masteryId, operationId, 0L,
                    current -> engine.purchase(current, nodeId, clock.now()));
            updateCache(playerId, masteryId, commit.after());
        }
        if (commit.applied()) {
            events.publish(new CombatMasteryNodeUnlocked(UUID.randomUUID(), clock.now(), playerId,
                    masteryId, nodeId, commit.before().rank(nodeId), commit.after().rank(nodeId),
                    commit.after().unspentPoints()));
        }
        return commit;
    }

    @Override
    public MasteryMutationCommit respec(UUID playerId, ContentId masteryId,
                                        OperationId operationId) {
        var definition = requireDefinition(masteryId);
        CombatMasteryEngine engine = new CombatMasteryEngine(definition, content.get().masteryNodes());
        MasteryMutationCommit commit;
        synchronized (lock(playerId)) {
            commit = repository.mutate(playerId, masteryId, operationId, 0L,
                    current -> engine.respec(current, clock.now()));
            updateCache(playerId, masteryId, commit.after());
        }
        return commit;
    }

    public void activate(UUID playerId) {
        active.put(playerId, Map.copyOf(repository.load(playerId)));
    }

    public void forget(UUID playerId) {
        active.remove(playerId);
    }

    private void updateCache(UUID playerId, ContentId masteryId, MasterySnapshot snapshot) {
        active.compute(playerId, (ignored, current) -> {
            Map<ContentId, MasterySnapshot> next = new java.util.HashMap<>(
                    current == null ? Map.of() : current);
            next.put(masteryId, snapshot);
            return Map.copyOf(next);
        });
    }

    private Object lock(UUID playerId) {
        return mutationLocks[(playerId.hashCode() & Integer.MAX_VALUE) % mutationLocks.length];
    }

    private static Object[] locks() {
        Object[] result = new Object[64];
        java.util.Arrays.setAll(result, ignored -> new Object());
        return result;
    }

    private com.branz.mmorpg.api.mastery.MasteryDefinition requireDefinition(ContentId masteryId) {
        var definition = content.get().masteries().get(masteryId);
        if (definition == null) throw new IllegalArgumentException("unknown combat mastery " + masteryId);
        return definition;
    }

    private void publishLevelChange(UUID playerId, ContentId masteryId,
                                    MasteryMutationCommit commit) {
        if (commit.before().level() == commit.after().level()) return;
        events.publish(new CombatMasteryLevelChanged(UUID.randomUUID(), clock.now(), playerId,
                masteryId, commit.before().level(), commit.after().level(),
                commit.after().unspentPoints() - commit.before().unspentPoints()));
    }
}
