package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.EncounterId;
import java.util.Objects;

public record DownedEncounterStateCommit(
        EncounterId encounterId,
        int attempt,
        boolean recoverable,
        long expectedVersion,
        String replacementPayloadJson) {
    public DownedEncounterStateCommit {
        Objects.requireNonNull(encounterId, "encounterId");
        replacementPayloadJson = requireText(replacementPayloadJson, "replacementPayloadJson");
        if (attempt < 1 || expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "attempt must be positive and expectedVersion must not be negative");
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
