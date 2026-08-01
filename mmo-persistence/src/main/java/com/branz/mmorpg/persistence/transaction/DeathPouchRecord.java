package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.TransactionId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeathPouchRecord(
        UUID pouchId,
        UUID deathId,
        CharacterId ownerCharacterId,
        long amount,
        UUID walletDebitOperationId,
        UUID walletCreditOperationId,
        String worldKey,
        double locationX,
        double locationY,
        double locationZ,
        DeathPouchState state,
        String payloadJson,
        String contentVersion,
        long version,
        TransactionId lastTransactionId,
        Instant createdAt,
        Instant expiresAt,
        Instant updatedAt) {
    public DeathPouchRecord {
        Objects.requireNonNull(pouchId, "pouchId");
        Objects.requireNonNull(deathId, "deathId");
        Objects.requireNonNull(ownerCharacterId, "ownerCharacterId");
        Objects.requireNonNull(walletDebitOperationId, "walletDebitOperationId");
        Objects.requireNonNull(walletCreditOperationId, "walletCreditOperationId");
        worldKey = requireText(worldKey, "worldKey");
        Objects.requireNonNull(state, "state");
        payloadJson = requireText(payloadJson, "payloadJson");
        contentVersion = requireText(contentVersion, "contentVersion");
        Objects.requireNonNull(lastTransactionId, "lastTransactionId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (amount < 1
                || version < 1
                || !Double.isFinite(locationX)
                || !Double.isFinite(locationY)
                || !Double.isFinite(locationZ)
                || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("invalid death pouch amount/version/expiry");
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
