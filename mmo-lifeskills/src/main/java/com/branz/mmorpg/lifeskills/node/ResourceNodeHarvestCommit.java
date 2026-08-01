package com.branz.mmorpg.lifeskills.node;

import com.branz.mmorpg.api.identity.CharacterId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Frozen value intent emitted once at the authored work commit point. */
public record ResourceNodeHarvestCommit(
        UUID reservationId,
        CharacterId owner,
        UUID toolItemId,
        UUID yieldSeed,
        int durabilityCost,
        int focusCost,
        int remainingCharges,
        Optional<Instant> recoversAt) {
    public ResourceNodeHarvestCommit {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(toolItemId, "toolItemId");
        Objects.requireNonNull(yieldSeed, "yieldSeed");
        recoversAt = Objects.requireNonNull(recoversAt, "recoversAt");
        if (durabilityCost < 1
                || focusCost < 0
                || focusCost > 5
                || remainingCharges < 0
                || (remainingCharges == 0) != recoversAt.isPresent()) {
            throw new IllegalArgumentException("invalid resource node harvest commit");
        }
    }
}
