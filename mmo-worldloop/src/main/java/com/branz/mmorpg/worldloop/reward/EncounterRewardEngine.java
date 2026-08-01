package com.branz.mmorpg.worldloop.reward;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Pure personal eligibility freeze and deterministic per-character grant identity resolver. */
public final class EncounterRewardEngine {
    public RewardFreezeResult freeze(
            EncounterId encounterId,
            int attempt,
            long completionTick,
            long encounterRollSeed,
            RewardEligibilityProfile profile,
            List<RewardParticipantEvidence> participants) {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(participants, "participants");
        if (attempt < 1 || completionTick < 0) {
            throw new IllegalArgumentException("invalid reward freeze attempt/tick");
        }
        HashMap<CharacterId, PersonalRewardGrant> grants = new HashMap<>();
        HashMap<CharacterId, RewardIneligibilityReason> rejected = new HashMap<>();
        for (RewardParticipantEvidence participant : participants) {
            Objects.requireNonNull(participant, "participant");
            if (grants.containsKey(participant.characterId())
                    || rejected.containsKey(participant.characterId())) {
                throw new IllegalArgumentException("duplicate reward participant");
            }
            RewardIneligibilityReason reason = rejection(participant, completionTick, profile);
            if (reason != null) {
                rejected.put(participant.characterId(), reason);
                continue;
            }
            UUID grantId = grantId(encounterId, attempt, participant.characterId());
            grants.put(
                    participant.characterId(),
                    new PersonalRewardGrant(
                            participant.characterId(),
                            grantId,
                            rollSeed(encounterRollSeed, participant.characterId())));
        }
        return new RewardFreezeResult(grants, rejected);
    }

    private static RewardIneligibilityReason rejection(
            RewardParticipantEvidence participant,
            long completionTick,
            RewardEligibilityProfile profile) {
        if (participant.completionGrantAlreadyCommitted()) {
            return RewardIneligibilityReason.ALREADY_GRANTED;
        }
        if (!participant.joinedBeforeEligibilityCutoff()) {
            return RewardIneligibilityReason.JOINED_AFTER_CUTOFF;
        }
        if (!participant.validEncounterMembershipOrRecovery()) {
            return RewardIneligibilityReason.INVALID_MEMBERSHIP;
        }
        if (completionTick < participant.lastActiveTick()
                || completionTick - participant.lastActiveTick() > profile.maximumIdleTicks()) {
            return RewardIneligibilityReason.INACTIVE;
        }
        return profile.meaningful(participant.contribution())
                ? null
                : RewardIneligibilityReason.INSUFFICIENT_CONTRIBUTION;
    }

    private static UUID grantId(EncounterId encounterId, int attempt, CharacterId characterId) {
        String source =
                "personal-reward:"
                        + encounterId.value()
                        + ":"
                        + attempt
                        + ":"
                        + characterId.value();
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private static long rollSeed(long encounterSeed, CharacterId characterId) {
        UUID value = characterId.value();
        byte[] bytes =
                ByteBuffer.allocate(Long.BYTES * 3)
                        .putLong(encounterSeed)
                        .putLong(value.getMostSignificantBits())
                        .putLong(value.getLeastSignificantBits())
                        .array();
        UUID mixed = UUID.nameUUIDFromBytes(bytes);
        return mixed.getMostSignificantBits() ^ mixed.getLeastSignificantBits();
    }
}
