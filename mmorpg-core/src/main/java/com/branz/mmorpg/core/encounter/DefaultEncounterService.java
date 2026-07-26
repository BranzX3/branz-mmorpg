package com.branz.mmorpg.core.encounter;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.encounter.ContributionType;
import com.branz.mmorpg.api.encounter.EncounterDefinition;
import com.branz.mmorpg.api.encounter.EncounterRepository;
import com.branz.mmorpg.api.encounter.EncounterService;
import com.branz.mmorpg.api.encounter.EncounterSnapshot;
import com.branz.mmorpg.api.item.LootService;
import com.branz.mmorpg.api.runtime.GameClock;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class DefaultEncounterService implements EncounterService {
    private final EncounterRepository repository;
    private final LootService loot;
    private final Supplier<ContentSnapshot> content;
    private final GameClock clock;
    private final EncounterEngine engine = new EncounterEngine();

    public DefaultEncounterService(EncounterRepository repository, LootService loot,
                                   Supplier<ContentSnapshot> content, GameClock clock) {
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.loot = java.util.Objects.requireNonNull(loot, "loot");
        this.content = java.util.Objects.requireNonNull(content, "content");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    @Override public EncounterSnapshot create(
            ContentId definitionId, Set<UUID> participants) {
        return create(UUID.randomUUID(), definitionId, participants);
    }

    @Override public EncounterSnapshot create(
            UUID instanceId, ContentId definitionId, Set<UUID> participants) {
        EncounterDefinition definition = definition(definitionId);
        EncounterSnapshot existing = repository.find(instanceId).orElse(null);
        if (existing != null) {
            if (!existing.definitionId().equals(definitionId)
                    || !existing.participantSnapshot().equals(participants)) {
                throw new IllegalStateException(
                        "durable encounter ID was reused with different inputs");
            }
            return existing;
        }
        EncounterSnapshot created = repository.insert(
                engine.create(instanceId, definition, participants, clock.now()));
        return repository.save(engine.prepare(created, clock.now()));
    }

    @Override public EncounterSnapshot activate(
            UUID instanceId, Set<UUID> actorIds, Set<String> forcedChunks) {
        EncounterSnapshot current = require(instanceId);
        return repository.save(engine.activate(
                current, definition(current.definitionId()), actorIds, forcedChunks, clock.now()));
    }

    @Override public EncounterSnapshot contribute(
            UUID instanceId, UUID playerId, ContributionType type, double amount) {
        return repository.save(engine.contribute(
                require(instanceId), playerId, type, amount));
    }

    @Override public EncounterSnapshot bossHealth(UUID instanceId, double healthFraction) {
        EncounterSnapshot current = require(instanceId);
        return repository.save(engine.bossHealth(
                current, definition(current.definitionId()), healthFraction, clock.now()));
    }

    @Override public EncounterSnapshot connect(UUID instanceId, UUID playerId) {
        EncounterSnapshot current = require(instanceId);
        EncounterSnapshot changed = engine.connect(current, playerId, clock.now());
        return changed == current ? current : repository.save(changed);
    }

    @Override public EncounterSnapshot disconnect(UUID instanceId, UUID playerId) {
        EncounterSnapshot current = require(instanceId);
        EncounterSnapshot changed = engine.disconnect(current, playerId, clock.now());
        return changed == current ? current : repository.save(changed);
    }

    @Override public EncounterSnapshot checkWipe(UUID instanceId) {
        EncounterSnapshot current = require(instanceId);
        EncounterSnapshot changed = engine.wipe(
                current, definition(current.definitionId()), clock.now());
        return changed == current ? current : repository.save(changed);
    }

    @Override public EncounterSnapshot abandon(UUID instanceId) {
        EncounterSnapshot current = require(instanceId);
        EncounterSnapshot changed = engine.abandon(current, clock.now());
        return changed == current ? current : repository.save(changed);
    }

    @Override public EncounterSnapshot deliverRewards(UUID instanceId) {
        EncounterSnapshot current = require(instanceId);
        EncounterDefinition definition = definition(current.definitionId());
        String completionId = current.completionId()
                .orElseThrow(() -> new IllegalStateException("encounter is not complete"));
        for (UUID playerId : engine.eligibleRewards(current, definition)) {
            if (current.rewardedPlayers().contains(playerId)) continue;
            loot.resolvePersonal(playerId, definition.rewardLootTableId(),
                    completionId + ":player:" + playerId, true, Set.of(), Map.of());
            // The loot operation is idempotent, so a crash before this marker is safe to retry.
            current = repository.save(engine.markRewarded(current, playerId));
        }
        return current;
    }

    @Override public EncounterSnapshot beginCleanup(UUID instanceId) {
        EncounterSnapshot current = require(instanceId);
        return repository.save(engine.beginCleanup(current, clock.now()));
    }

    @Override public EncounterSnapshot acknowledgeCleanup(
            UUID instanceId, Set<UUID> removedActors, Set<String> releasedChunks) {
        EncounterSnapshot current = require(instanceId);
        EncounterSnapshot changed =
                engine.cleanup(current, removedActors, releasedChunks, clock.now());
        return changed == current ? current : repository.save(changed);
    }

    @Override public Collection<EncounterSnapshot> recoverable() {
        return repository.recoverable();
    }

    private EncounterSnapshot require(UUID id) {
        return repository.find(id).orElseThrow(
                () -> new IllegalArgumentException("unknown encounter " + id));
    }

    private EncounterDefinition definition(ContentId id) {
        EncounterDefinition result = content.get().encounters().get(id);
        if (result == null) throw new IllegalArgumentException("unknown encounter " + id);
        return result;
    }
}
