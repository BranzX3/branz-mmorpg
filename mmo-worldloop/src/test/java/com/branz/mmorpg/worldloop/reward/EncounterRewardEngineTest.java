package com.branz.mmorpg.worldloop.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EncounterRewardEngineTest {
    private final EncounterRewardEngine engine = new EncounterRewardEngine();
    private final RewardEligibilityProfile profile =
            new RewardEligibilityProfile(100, 50, 75, 1, 200);

    @Test
    void independentContributionCategoriesQualifyWithoutLastHitBias() {
        RewardParticipantEvidence damage = evidence(contribution(100, 0, 0, 0));
        RewardParticipantEvidence guard = evidence(contribution(0, 50, 0, 0));
        RewardParticipantEvidence support = evidence(contribution(0, 0, 75, 0));
        RewardParticipantEvidence objective = evidence(contribution(0, 0, 0, 1));

        RewardFreezeResult result = freeze(damage, guard, support, objective);
        assertEquals(4, result.grants().size());
        assertEquals(0, result.rejected().size());
    }

    @Test
    void cutoffMembershipAfkContributionAndReplayReasonsAreExplicit() {
        RewardParticipantEvidence late =
                new RewardParticipantEvidence(
                        character(), 1, 990, false, true, false, contribution(100, 0, 0, 0));
        RewardParticipantEvidence invalid =
                new RewardParticipantEvidence(
                        character(), 1, 990, true, false, false, contribution(100, 0, 0, 0));
        RewardParticipantEvidence afk =
                new RewardParticipantEvidence(
                        character(), 1, 799, true, true, false, contribution(100, 0, 0, 0));
        RewardParticipantEvidence idle = evidence(contribution(99, 49, 74, 0));
        RewardParticipantEvidence granted =
                new RewardParticipantEvidence(
                        character(), 1, 990, true, true, true, contribution(100, 0, 0, 0));

        RewardFreezeResult result = freeze(late, invalid, afk, idle, granted);
        assertEquals(RewardIneligibilityReason.JOINED_AFTER_CUTOFF, reason(result, late));
        assertEquals(RewardIneligibilityReason.INVALID_MEMBERSHIP, reason(result, invalid));
        assertEquals(RewardIneligibilityReason.INACTIVE, reason(result, afk));
        assertEquals(RewardIneligibilityReason.INSUFFICIENT_CONTRIBUTION, reason(result, idle));
        assertEquals(RewardIneligibilityReason.ALREADY_GRANTED, reason(result, granted));
    }

    @Test
    void grantIdentityAndRollSeedAreStablePerEncounterAttemptAndCharacter() {
        RewardParticipantEvidence participant = evidence(contribution(100, 0, 0, 0));
        EncounterId encounter = encounter();
        RewardFreezeResult first =
                engine.freeze(encounter, 2, 1000, 42, profile, List.of(participant));
        RewardFreezeResult replay =
                engine.freeze(encounter, 2, 1000, 42, profile, List.of(participant));
        PersonalRewardGrant grant = first.grants().get(participant.characterId());
        assertEquals(grant, replay.grants().get(participant.characterId()));

        PersonalRewardGrant nextAttempt =
                engine.freeze(encounter, 3, 1000, 42, profile, List.of(participant))
                        .grants()
                        .get(participant.characterId());
        assertNotEquals(grant.grantId(), nextAttempt.grantId());
        assertEquals(grant.rollSeed(), nextAttempt.rollSeed());
    }

    @Test
    void validRecoveryCanQualifyWhileInvalidPresenceCannot() {
        RewardParticipantEvidence recovered =
                new RewardParticipantEvidence(
                        character(), 1, 990, true, true, false, contribution(0, 0, 75, 0));
        RewardParticipantEvidence absent =
                new RewardParticipantEvidence(
                        character(), 1, 990, true, false, false, contribution(0, 0, 75, 0));
        RewardFreezeResult result = freeze(recovered, absent);
        assertEquals(
                recovered.characterId(),
                result.grants().get(recovered.characterId()).characterId());
        assertEquals(RewardIneligibilityReason.INVALID_MEMBERSHIP, reason(result, absent));
    }

    @Test
    void invalidTicksAndDuplicateParticipantsFailClosed() {
        RewardParticipantEvidence participant = evidence(contribution(100, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        engine.freeze(
                                encounter(),
                                1,
                                1000,
                                1,
                                profile,
                                List.of(participant, participant)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RewardParticipantEvidence(
                                character(), 10, 9, true, true, false, contribution(1, 0, 0, 0)));
    }

    private RewardFreezeResult freeze(RewardParticipantEvidence... participants) {
        return engine.freeze(encounter(), 1, 1000, 42, profile, List.of(participants));
    }

    private static RewardParticipantEvidence evidence(RewardContribution contribution) {
        return new RewardParticipantEvidence(character(), 1, 990, true, true, false, contribution);
    }

    private static RewardContribution contribution(
            long damage, long guard, long support, long objective) {
        return new RewardContribution(damage, guard, support, objective);
    }

    private static RewardIneligibilityReason reason(
            RewardFreezeResult result, RewardParticipantEvidence participant) {
        return result.rejected().get(participant.characterId());
    }

    private static EncounterId encounter() {
        return new EncounterId(UUID.randomUUID());
    }

    private static CharacterId character() {
        return new CharacterId(UUID.randomUUID());
    }
}
