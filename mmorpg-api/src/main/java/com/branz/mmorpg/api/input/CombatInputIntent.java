package com.branz.mmorpg.api.input;

import com.branz.mmorpg.api.combat.WorldPoint;
import com.branz.mmorpg.api.player.SessionToken;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable server-authored input intent; target hints are revalidated later. */
public record CombatInputIntent(
        UUID inputId,
        UUID playerId,
        SessionToken sessionToken,
        CombatInputKey input,
        long pressedAtTick,
        long monotonicNanos,
        Optional<UUID> heldItemInstanceId,
        Optional<UUID> targetHint,
        WorldPoint position,
        long inputProfileRevision,
        long contentRevision,
        long loadoutRevision) {

    public CombatInputIntent {
        Objects.requireNonNull(inputId, "inputId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sessionToken, "sessionToken");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(heldItemInstanceId, "heldItemInstanceId");
        Objects.requireNonNull(targetHint, "targetHint");
        Objects.requireNonNull(position, "position");
        if (pressedAtTick < 0 || monotonicNanos < 0 || inputProfileRevision < 1
                || contentRevision < 1 || loadoutRevision < 0) {
            throw new IllegalArgumentException("invalid combat input intent revision/time");
        }
    }
}
