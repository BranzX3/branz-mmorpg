package com.branz.mmorpg.worldloop.death;

import com.branz.mmorpg.api.identity.CharacterId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Pure 10% carried-wallet death-pouch planner with stable saga identities. */
public final class DeathPouchEngine {
    public static final Duration RETENTION = Duration.ofDays(7);

    public DeathPouchDecision plan(
            UUID deathId,
            CharacterId ownerCharacterId,
            DeathPouchContext context,
            long carriedWalletAmount,
            DeathPouchLocation location,
            Instant createdAt) {
        Objects.requireNonNull(deathId, "deathId");
        Objects.requireNonNull(ownerCharacterId, "ownerCharacterId");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(createdAt, "createdAt");
        if (carriedWalletAmount < 0) {
            throw new IllegalArgumentException("carried wallet amount must not be negative");
        }
        if (context == DeathPouchContext.BOSS_SUPPRESSED) {
            return suppressed(DeathPouchDecisionReason.BOSS_PROFILE_SUPPRESSED);
        }
        if (context == DeathPouchContext.DUEL || context == DeathPouchContext.ARENA) {
            return suppressed(DeathPouchDecisionReason.PVP_PROFILE_SUPPRESSED);
        }
        long amount = carriedWalletAmount / 10;
        if (amount == 0) {
            return suppressed(DeathPouchDecisionReason.CARRIED_WALLET_TOO_SMALL);
        }
        UUID pouchId = named("death-pouch:" + deathId + ":pouch");
        UUID debitId = named("death-pouch:" + deathId + ":wallet-debit");
        return new DeathPouchDecision(
                DeathPouchDecisionReason.POUCH_PLANNED,
                Optional.of(
                        new DeathPouchDraft(
                                pouchId,
                                deathId,
                                ownerCharacterId,
                                amount,
                                debitId,
                                location,
                                createdAt,
                                createdAt.plus(RETENTION))));
    }

    private static DeathPouchDecision suppressed(DeathPouchDecisionReason reason) {
        return new DeathPouchDecision(reason, Optional.empty());
    }

    private static UUID named(String source) {
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }
}
