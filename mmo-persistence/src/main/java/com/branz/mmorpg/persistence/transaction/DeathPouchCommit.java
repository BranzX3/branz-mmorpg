package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeathPouchCommit(
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
        Instant createdAt,
        Instant expiresAt,
        DeathPouchState state,
        long expectedVersion,
        String replacementPayloadJson) {
    public DeathPouchCommit {
        Objects.requireNonNull(pouchId, "pouchId");
        Objects.requireNonNull(deathId, "deathId");
        Objects.requireNonNull(ownerCharacterId, "ownerCharacterId");
        Objects.requireNonNull(walletDebitOperationId, "walletDebitOperationId");
        Objects.requireNonNull(walletCreditOperationId, "walletCreditOperationId");
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(replacementPayloadJson, "replacementPayloadJson");
        if (amount < 1
                || expectedVersion < 0
                || worldKey.isBlank()
                || !Double.isFinite(locationX)
                || !Double.isFinite(locationY)
                || !Double.isFinite(locationZ)
                || replacementPayloadJson.isBlank()
                || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("invalid death pouch commit");
        }
        if (expectedVersion == 0 && state != DeathPouchState.PENDING_DEBIT) {
            throw new IllegalArgumentException("new death pouch must start PENDING_DEBIT");
        }
    }
}
