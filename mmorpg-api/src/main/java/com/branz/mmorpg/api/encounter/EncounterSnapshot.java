package com.branz.mmorpg.api.encounter;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record EncounterSnapshot(
        UUID instanceId,
        ContentId definitionId,
        EncounterState state,
        int phaseIndex,
        int attempt,
        Set<UUID> participantSnapshot,
        Set<UUID> connectedParticipants,
        Map<UUID, Map<ContributionType, Double>> contributions,
        Set<UUID> actorIds,
        Set<String> forcedChunkKeys,
        Optional<String> completionId,
        Set<UUID> rewardedPlayers,
        Instant createdAt,
        Instant stateSince,
        long revision) {
    public EncounterSnapshot {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(state, "state");
        participantSnapshot = Set.copyOf(participantSnapshot);
        connectedParticipants = Set.copyOf(connectedParticipants);
        contributions = contributions.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
        actorIds = Set.copyOf(actorIds);
        forcedChunkKeys = Set.copyOf(forcedChunkKeys);
        Objects.requireNonNull(completionId, "completionId");
        rewardedPlayers = Set.copyOf(rewardedPlayers);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(stateSince, "stateSince");
        if (phaseIndex < 0 || attempt < 1 || revision < 0) {
            throw new IllegalArgumentException("invalid encounter snapshot");
        }
    }
}
