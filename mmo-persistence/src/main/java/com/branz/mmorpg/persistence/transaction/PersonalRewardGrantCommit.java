package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import java.util.Objects;
import java.util.UUID;

public record PersonalRewardGrantCommit(
        UUID grantId,
        EncounterId encounterId,
        int attempt,
        CharacterId characterId,
        long rollSeed,
        PersonalRewardGrantState state,
        long expectedVersion,
        String replacementPayloadJson) {
    public PersonalRewardGrantCommit {
        Objects.requireNonNull(grantId, "grantId");
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(replacementPayloadJson, "replacementPayloadJson");
        if (replacementPayloadJson.isBlank()) {
            throw new IllegalArgumentException("replacementPayloadJson must not be blank");
        }
        if (attempt < 1 || expectedVersion < 0) {
            throw new IllegalArgumentException("attempt must be positive and version non-negative");
        }
        if (expectedVersion == 0 && state != PersonalRewardGrantState.FROZEN) {
            throw new IllegalArgumentException("new personal reward grant must start FROZEN");
        }
    }
}
