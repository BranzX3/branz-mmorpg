package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;

/** Compare-and-set replacement of one character's durable Lifeskill/Focus document. */
public record CharacterLifeskillStateMutation(
        CharacterId characterId, long expectedVersion, String replacementPayloadJson) {
    public CharacterLifeskillStateMutation {
        Objects.requireNonNull(characterId, "characterId");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
        replacementPayloadJson = requireJson(replacementPayloadJson);
    }

    private static String requireJson(String value) {
        Objects.requireNonNull(value, "replacementPayloadJson");
        if (value.isBlank()) {
            throw new IllegalArgumentException("replacementPayloadJson must not be blank");
        }
        return value;
    }
}
