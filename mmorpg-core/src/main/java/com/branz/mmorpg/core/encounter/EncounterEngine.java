package com.branz.mmorpg.core.encounter;

import com.branz.mmorpg.api.encounter.ContributionType;
import com.branz.mmorpg.api.encounter.EncounterDefinition;
import com.branz.mmorpg.api.encounter.EncounterSnapshot;
import com.branz.mmorpg.api.encounter.EncounterState;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pure encounter lifecycle, contribution, recovery, and cleanup state machine. */
public final class EncounterEngine {
    public EncounterSnapshot create(
            UUID instanceId, EncounterDefinition definition,
            Set<UUID> participants, Instant now) {
        if (participants.size() < definition.minimumPlayers()
                || participants.size() > definition.maximumPlayers()) {
            throw new IllegalArgumentException("participant count is outside encounter limits");
        }
        return new EncounterSnapshot(instanceId, definition.id(), EncounterState.WAITING,
                0, 1, participants, participants, Map.of(), Set.of(), Set.of(),
                Optional.empty(), Set.of(), now, now, 0);
    }

    public EncounterSnapshot prepare(EncounterSnapshot current, Instant now) {
        require(current, EncounterState.WAITING);
        return copy(current, EncounterState.PREPARING, current.phaseIndex(),
                current.connectedParticipants(), current.contributions(), current.actorIds(),
                current.forcedChunkKeys(), current.completionId(), current.rewardedPlayers(), now);
    }

    public EncounterSnapshot activate(
            EncounterSnapshot current, EncounterDefinition definition,
            Set<UUID> actorIds, Set<String> forcedChunks, Instant now) {
        require(current, EncounterState.PREPARING);
        if (now.isBefore(current.stateSince().plusMillis(definition.preparationMillis()))) {
            throw new IllegalStateException("encounter preparation is not complete");
        }
        if (actorIds.isEmpty()) throw new IllegalArgumentException("encounter requires actors");
        return copy(current, EncounterState.ACTIVE, 0, current.connectedParticipants(),
                current.contributions(), actorIds, forcedChunks, Optional.empty(),
                current.rewardedPlayers(), now);
    }

    public EncounterSnapshot connect(
            EncounterSnapshot current, UUID playerId, Instant now) {
        if (!current.participantSnapshot().contains(playerId)) return current;
        HashSet<UUID> connected = new HashSet<>(current.connectedParticipants());
        if (!connected.add(playerId)) return current;
        return copy(current, current.state(), current.phaseIndex(), connected,
                current.contributions(), current.actorIds(), current.forcedChunkKeys(),
                current.completionId(), current.rewardedPlayers(), current.stateSince());
    }

    public EncounterSnapshot disconnect(
            EncounterSnapshot current, UUID playerId, Instant now) {
        HashSet<UUID> connected = new HashSet<>(current.connectedParticipants());
        if (!connected.remove(playerId)) return current;
        return copy(current, current.state(), current.phaseIndex(), connected,
                current.contributions(), current.actorIds(), current.forcedChunkKeys(),
                current.completionId(), current.rewardedPlayers(),
                connected.isEmpty() ? now : current.stateSince());
    }

    public EncounterSnapshot contribute(
            EncounterSnapshot current, UUID playerId, ContributionType type, double amount) {
        require(current, EncounterState.ACTIVE);
        if (!current.participantSnapshot().contains(playerId)) {
            throw new IllegalArgumentException("player is not in encounter start snapshot");
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("contribution must be positive");
        }
        Map<UUID, Map<ContributionType, Double>> totals = mutable(current.contributions());
        totals.computeIfAbsent(playerId, ignored -> new EnumMap<>(ContributionType.class))
                .merge(type, amount, Double::sum);
        return copy(current, current.state(), current.phaseIndex(),
                current.connectedParticipants(), totals, current.actorIds(),
                current.forcedChunkKeys(), current.completionId(),
                current.rewardedPlayers(), current.stateSince());
    }

    public EncounterSnapshot bossHealth(
            EncounterSnapshot current, EncounterDefinition definition,
            double healthFraction, Instant now) {
        require(current, EncounterState.ACTIVE);
        if (!Double.isFinite(healthFraction) || healthFraction < 0 || healthFraction > 1) {
            throw new IllegalArgumentException("invalid boss health fraction");
        }
        if (healthFraction == 0) {
            String completionId = "encounter:" + current.instanceId() + ':' + current.attempt();
            return copy(current, EncounterState.SUCCESS,
                    definition.phases().size() - 1, current.connectedParticipants(),
                    current.contributions(), current.actorIds(), current.forcedChunkKeys(),
                    Optional.of(completionId), current.rewardedPlayers(), now);
        }
        int phase = current.phaseIndex();
        for (int index = phase + 1; index < definition.phases().size(); index++) {
            double entryThreshold = definition.phases().get(index - 1).healthFractionThreshold();
            if (healthFraction <= entryThreshold) phase = index;
        }
        if (phase == current.phaseIndex()) return current;
        return copy(current, current.state(), phase, current.connectedParticipants(),
                current.contributions(), current.actorIds(), current.forcedChunkKeys(),
                current.completionId(), current.rewardedPlayers(), now);
    }

    public EncounterSnapshot wipe(
            EncounterSnapshot current, EncounterDefinition definition, Instant now) {
        require(current, EncounterState.ACTIVE);
        if (!current.connectedParticipants().isEmpty()) return current;
        if (now.isBefore(current.stateSince().plusMillis(definition.wipeGraceMillis()))) {
            return current;
        }
        return copy(current, EncounterState.FAILED, current.phaseIndex(), Set.of(),
                current.contributions(), current.actorIds(), current.forcedChunkKeys(),
                Optional.empty(), current.rewardedPlayers(), now);
    }

    public EncounterSnapshot abandon(EncounterSnapshot current, Instant now) {
        if (current.state() == EncounterState.FAILED) return current;
        if (current.state() != EncounterState.WAITING
                && current.state() != EncounterState.PREPARING
                && current.state() != EncounterState.ACTIVE) {
            throw new IllegalStateException(
                    "encounter cannot be abandoned from " + current.state());
        }
        return copy(current, EncounterState.FAILED, current.phaseIndex(),
                current.connectedParticipants(), current.contributions(), current.actorIds(),
                current.forcedChunkKeys(), Optional.empty(), current.rewardedPlayers(), now);
    }

    public EncounterSnapshot beginCleanup(EncounterSnapshot current, Instant now) {
        if (current.state() != EncounterState.SUCCESS
                && current.state() != EncounterState.FAILED
                && current.state() != EncounterState.CLEANING) {
            throw new IllegalStateException("encounter is not terminal");
        }
        if (current.state() == EncounterState.CLEANING) return current;
        return copy(current, EncounterState.CLEANING, current.phaseIndex(),
                current.connectedParticipants(), current.contributions(), current.actorIds(),
                current.forcedChunkKeys(), current.completionId(),
                current.rewardedPlayers(), now);
    }

    /** Repeated cleanup acknowledgements are safe and converge to CLOSED. */
    public EncounterSnapshot cleanup(
            EncounterSnapshot current, Set<UUID> removedActors,
            Set<String> releasedChunks, Instant now) {
        if (current.state() == EncounterState.CLOSED) return current;
        require(current, EncounterState.CLEANING);
        HashSet<UUID> actors = new HashSet<>(current.actorIds());
        actors.removeAll(removedActors);
        HashSet<String> chunks = new HashSet<>(current.forcedChunkKeys());
        chunks.removeAll(releasedChunks);
        EncounterState state = actors.isEmpty() && chunks.isEmpty()
                ? EncounterState.CLOSED : EncounterState.CLEANING;
        return copy(current, state, current.phaseIndex(), current.connectedParticipants(),
                current.contributions(), actors, chunks, current.completionId(),
                current.rewardedPlayers(), state == EncounterState.CLOSED
                        ? now : current.stateSince());
    }

    public Set<UUID> eligibleRewards(
            EncounterSnapshot current, EncounterDefinition definition) {
        if (current.state() != EncounterState.SUCCESS
                && current.state() != EncounterState.CLEANING
                && current.state() != EncounterState.CLOSED) return Set.of();
        HashSet<UUID> eligible = new HashSet<>();
        current.participantSnapshot().forEach(player -> {
            double total = current.contributions().getOrDefault(player, Map.of())
                    .values().stream().mapToDouble(Double::doubleValue).sum();
            if (total >= definition.minimumContribution()) eligible.add(player);
        });
        return Set.copyOf(eligible);
    }

    public EncounterSnapshot markRewarded(EncounterSnapshot current, UUID playerId) {
        if (current.completionId().isEmpty()) {
            throw new IllegalStateException("encounter has no completion ID");
        }
        HashSet<UUID> rewarded = new HashSet<>(current.rewardedPlayers());
        if (!rewarded.add(playerId)) return current;
        return copy(current, current.state(), current.phaseIndex(),
                current.connectedParticipants(), current.contributions(), current.actorIds(),
                current.forcedChunkKeys(), current.completionId(), rewarded,
                current.stateSince());
    }

    private static Map<UUID, Map<ContributionType, Double>> mutable(
            Map<UUID, Map<ContributionType, Double>> source) {
        HashMap<UUID, Map<ContributionType, Double>> result = new HashMap<>();
        source.forEach((player, values) ->
                result.put(player, new EnumMap<>(values)));
        return result;
    }

    private static EncounterSnapshot copy(
            EncounterSnapshot source, EncounterState state, int phase,
            Set<UUID> connected, Map<UUID, Map<ContributionType, Double>> contributions,
            Set<UUID> actors, Set<String> chunks, Optional<String> completion,
            Set<UUID> rewarded, Instant stateSince) {
        return new EncounterSnapshot(source.instanceId(), source.definitionId(), state,
                phase, source.attempt(), source.participantSnapshot(), connected,
                contributions, actors, chunks, completion, rewarded, source.createdAt(),
                stateSince, source.revision() + 1);
    }

    private static void require(EncounterSnapshot current, EncounterState expected) {
        if (current.state() != expected) {
            throw new IllegalStateException("expected " + expected + " but was " + current.state());
        }
    }
}
