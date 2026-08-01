package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;

public record CharacterExpeditionStateCommit(
        CharacterId characterId, long expectedVersion, String replacementPayloadJson) {
    public CharacterExpeditionStateCommit {
        Objects.requireNonNull(characterId, "characterId");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
        Objects.requireNonNull(replacementPayloadJson, "replacementPayloadJson");
        if (replacementPayloadJson.isBlank()) {
            throw new IllegalArgumentException("replacementPayloadJson must not be blank");
        }
    }
}
