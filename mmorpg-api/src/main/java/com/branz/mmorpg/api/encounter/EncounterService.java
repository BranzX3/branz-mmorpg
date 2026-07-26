package com.branz.mmorpg.api.encounter;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface EncounterService {
    EncounterSnapshot create(ContentId definitionId, Set<UUID> participants);
    default EncounterSnapshot create(
            UUID instanceId, ContentId definitionId, Set<UUID> participants) {
        return create(definitionId, participants);
    }
    EncounterSnapshot activate(UUID instanceId, Set<UUID> actorIds, Set<String> forcedChunks);
    EncounterSnapshot contribute(UUID instanceId, UUID playerId,
                                 ContributionType type, double amount);
    EncounterSnapshot bossHealth(UUID instanceId, double healthFraction);
    EncounterSnapshot connect(UUID instanceId, UUID playerId);
    EncounterSnapshot disconnect(UUID instanceId, UUID playerId);
    EncounterSnapshot checkWipe(UUID instanceId);
    EncounterSnapshot abandon(UUID instanceId);
    EncounterSnapshot deliverRewards(UUID instanceId);
    EncounterSnapshot beginCleanup(UUID instanceId);
    EncounterSnapshot acknowledgeCleanup(
            UUID instanceId, Set<UUID> removedActors, Set<String> releasedChunks);
    Collection<EncounterSnapshot> recoverable();
}
