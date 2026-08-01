package com.branz.mmorpg.worldloop.reward;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.List;
import java.util.Objects;

public record EncounterRewardTable(
        DefinitionId encounterDefinitionId,
        RewardEligibilityProfile eligibilityProfile,
        double lateJoinHpRatio,
        List<RewardTableEntry> entries) {
    public EncounterRewardTable {
        Objects.requireNonNull(encounterDefinitionId, "encounterDefinitionId");
        Objects.requireNonNull(eligibilityProfile, "eligibilityProfile");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (!Double.isFinite(lateJoinHpRatio)
                || lateJoinHpRatio < 0
                || lateJoinHpRatio > 1
                || entries.isEmpty()
                || entries.size() > 16) {
            throw new IllegalArgumentException("invalid encounter reward table");
        }
        long total = 0;
        for (RewardTableEntry entry : entries) {
            total = Math.addExact(total, entry.weight());
        }
    }
}
