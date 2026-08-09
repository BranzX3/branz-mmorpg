package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.TransactionId;
import java.time.Instant;
import java.util.Objects;

/** Durable first-session foundation choice and starter-kit completion state. */
public record CharacterOnboardingStateRecord(
        CharacterId characterId,
        String foundationId,
        boolean kitReady,
        String contentVersion,
        long version,
        TransactionId lastTransactionId,
        Instant createdAt,
        Instant updatedAt) {
    public CharacterOnboardingStateRecord {
        Objects.requireNonNull(characterId, "characterId");
        foundationId = requireText(foundationId, "foundationId");
        contentVersion = requireText(contentVersion, "contentVersion");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        Objects.requireNonNull(lastTransactionId, "lastTransactionId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
