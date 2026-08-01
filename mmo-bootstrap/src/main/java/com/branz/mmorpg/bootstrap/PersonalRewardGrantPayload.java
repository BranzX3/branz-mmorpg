package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.worldloop.reward.RewardParticipantEvidence;
import com.branz.mmorpg.worldloop.reward.RolledPersonalReward;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

record PersonalRewardGrantPayload(
        UUID grantId,
        EncounterId encounterId,
        int attempt,
        CharacterId characterId,
        long rollSeed,
        RewardParticipantEvidence evidence,
        Optional<RolledPersonalReward> outcome,
        Optional<RewardDeliveryReceipt> delivery) {
    PersonalRewardGrantPayload {
        Objects.requireNonNull(grantId, "grantId");
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(evidence, "evidence");
        outcome = Objects.requireNonNull(outcome, "outcome");
        delivery = Objects.requireNonNull(delivery, "delivery");
        if (attempt < 1 || !characterId.equals(evidence.characterId())) {
            throw new IllegalArgumentException("invalid personal reward payload identity");
        }
        if (delivery.isPresent() && outcome.isEmpty()) {
            throw new IllegalArgumentException("delivery requires a rolled reward outcome");
        }
    }
}
