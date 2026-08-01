package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.identity.TransactionId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PersonalRewardGrantRecord(
        UUID grantId,
        EncounterId encounterId,
        int attempt,
        CharacterId characterId,
        long rollSeed,
        PersonalRewardGrantState state,
        String payloadJson,
        String contentVersion,
        long version,
        TransactionId lastTransactionId,
        Instant createdAt,
        Instant updatedAt) {
    public PersonalRewardGrantRecord {
        Objects.requireNonNull(grantId, "grantId");
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(state, "state");
        payloadJson = requireText(payloadJson, "payloadJson");
        contentVersion = requireText(contentVersion, "contentVersion");
        Objects.requireNonNull(lastTransactionId, "lastTransactionId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (attempt < 1 || version < 1) {
            throw new IllegalArgumentException("attempt and version must be positive");
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
