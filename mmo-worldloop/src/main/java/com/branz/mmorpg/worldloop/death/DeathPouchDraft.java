package com.branz.mmorpg.worldloop.death;

import com.branz.mmorpg.api.identity.CharacterId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Stable wallet debit/pouch identity planned before any external value effect. */
public record DeathPouchDraft(
        UUID pouchId,
        UUID deathId,
        CharacterId ownerCharacterId,
        long amount,
        UUID walletDebitOperationId,
        DeathPouchLocation location,
        Instant createdAt,
        Instant expiresAt) {
    public DeathPouchDraft {
        Objects.requireNonNull(pouchId, "pouchId");
        Objects.requireNonNull(deathId, "deathId");
        Objects.requireNonNull(ownerCharacterId, "ownerCharacterId");
        Objects.requireNonNull(walletDebitOperationId, "walletDebitOperationId");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (amount < 1 || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("death pouch amount/expiry is invalid");
        }
    }
}
