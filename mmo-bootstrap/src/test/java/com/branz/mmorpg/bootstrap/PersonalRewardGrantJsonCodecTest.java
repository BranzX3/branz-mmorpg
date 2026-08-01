package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.persistence.transaction.PersonalRewardGrantRecord;
import com.branz.mmorpg.persistence.transaction.PersonalRewardGrantState;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import com.branz.mmorpg.worldloop.reward.RewardContribution;
import com.branz.mmorpg.worldloop.reward.RewardParticipantEvidence;
import com.branz.mmorpg.worldloop.reward.RolledPersonalReward;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonalRewardGrantJsonCodecTest {
    private final PersonalRewardGrantJsonCodec codec = new PersonalRewardGrantJsonCodec();

    @Test
    void canonicalFrozenRolledAndDeliveredPayloadsRoundTrip() {
        PersonalRewardGrantPayload frozen = payload(Optional.empty(), Optional.empty());
        assertEquals(frozen, codec.decode(codec.encode(frozen), PersonalRewardGrantState.FROZEN));

        RolledPersonalReward outcome =
                new RolledPersonalReward(
                        DefinitionId.of("material.infusion_stock"),
                        2,
                        new LotId(UUID.randomUUID()));
        PersonalRewardGrantPayload rolled = payload(Optional.of(outcome), Optional.empty());
        assertEquals(rolled, codec.decode(codec.encode(rolled), PersonalRewardGrantState.ROLLED));

        PersonalRewardGrantPayload delivered =
                payload(
                        Optional.of(outcome),
                        Optional.of(
                                new RewardDeliveryReceipt(
                                        new TransactionId(UUID.randomUUID()),
                                        ValueLocation.pendingRewards("reward:test"))));
        String encoded = codec.encode(delivered);
        assertEquals(delivered, codec.decode(encoded, PersonalRewardGrantState.DELIVERED));
        assertEquals(
                encoded, codec.encode(codec.decode(encoded, PersonalRewardGrantState.DELIVERED)));
    }

    @Test
    void durableStateAndRecordIdentityMismatchFailClosed() {
        PersonalRewardGrantPayload frozen = payload(Optional.empty(), Optional.empty());
        String json = codec.encode(frozen);
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(json, PersonalRewardGrantState.ROLLED));
        PersonalRewardGrantRecord mismatched =
                new PersonalRewardGrantRecord(
                        UUID.randomUUID(),
                        frozen.encounterId(),
                        frozen.attempt(),
                        frozen.characterId(),
                        frozen.rollSeed(),
                        PersonalRewardGrantState.FROZEN,
                        json,
                        "test",
                        1,
                        new TransactionId(UUID.randomUUID()),
                        Instant.EPOCH,
                        Instant.EPOCH);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(mismatched));
    }

    @Test
    void unknownSchemaAndInvalidEvidenceAreRejected() {
        PersonalRewardGrantPayload frozen = payload(Optional.empty(), Optional.empty());
        String json = codec.encode(frozen);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        codec.decode(
                                json.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                                PersonalRewardGrantState.FROZEN));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        codec.decode(
                                json.replace("\"damageAndPosture\":100", "\"damageAndPosture\":-1"),
                                PersonalRewardGrantState.FROZEN));
    }

    private static PersonalRewardGrantPayload payload(
            Optional<RolledPersonalReward> outcome, Optional<RewardDeliveryReceipt> delivery) {
        CharacterId characterId = new CharacterId(UUID.randomUUID());
        return new PersonalRewardGrantPayload(
                UUID.randomUUID(),
                new EncounterId(UUID.randomUUID()),
                2,
                characterId,
                -17,
                new RewardParticipantEvidence(
                        characterId,
                        10,
                        90,
                        true,
                        true,
                        false,
                        new RewardContribution(100, 0, 0, 0)),
                outcome,
                delivery);
    }
}
