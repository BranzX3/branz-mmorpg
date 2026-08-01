package com.branz.mmorpg.worldloop.reward;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;

public record RewardParticipantEvidence(
        CharacterId characterId,
        long joinedTick,
        long lastActiveTick,
        boolean joinedBeforeEligibilityCutoff,
        boolean validEncounterMembershipOrRecovery,
        boolean completionGrantAlreadyCommitted,
        RewardContribution contribution) {
    public RewardParticipantEvidence {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(contribution, "contribution");
        if (joinedTick < 0 || lastActiveTick < joinedTick) {
            throw new IllegalArgumentException("invalid reward participation ticks");
        }
    }
}
