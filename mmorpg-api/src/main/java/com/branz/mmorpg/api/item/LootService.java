package com.branz.mmorpg.api.item;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface LootService {
    LootRollResult resolvePersonal(
            UUID playerId, ContentId lootTableId, String durableRollId,
            boolean contributionEligible, Set<String> conditions,
            Map<String, Integer> pityMisses);

    /**
     * Resolves one shared party roll and deterministically assigns each award
     * to one eligible participant. The durable roll ID makes retries stable.
     */
    default Map<UUID, LootRollResult> resolveParty(
            Set<UUID> eligiblePlayers, ContentId lootTableId, String durableRollId,
            Set<String> conditions, Map<String, Integer> pityMisses) {
        throw new UnsupportedOperationException("party loot is not supported by this provider");
    }
}
