package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.EncounterId;
import java.util.Objects;

public record BossEncounterStateCommit(
        EncounterId encounterId,
        DefinitionId definitionId,
        String phase,
        long expectedVersion,
        String replacementPayloadJson) {
    public BossEncounterStateCommit {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(definitionId, "definitionId");
        phase = requireText(phase, "phase");
        replacementPayloadJson = requireText(replacementPayloadJson, "replacementPayloadJson");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
