package com.branz.mmorpg.lifeskills.node;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ResourceNodeSlot(
        ResourceNodePhase phase,
        int remainingCharges,
        Optional<ResourceNodeReservation> reservation,
        Optional<Instant> recoversAt) {
    public ResourceNodeSlot {
        Objects.requireNonNull(phase, "phase");
        reservation = Objects.requireNonNull(reservation, "reservation");
        recoversAt = Objects.requireNonNull(recoversAt, "recoversAt");
        if (remainingCharges < 0) {
            throw new IllegalArgumentException("remaining charges must be non-negative");
        }
        boolean availableShape =
                phase == ResourceNodePhase.AVAILABLE
                        && remainingCharges > 0
                        && reservation.isEmpty()
                        && recoversAt.isEmpty();
        boolean reservedShape =
                phase == ResourceNodePhase.RESERVED
                        && remainingCharges > 0
                        && reservation.isPresent()
                        && recoversAt.isEmpty();
        boolean recoveryShape =
                (phase == ResourceNodePhase.DEPLETED || phase == ResourceNodePhase.RECOVERING)
                        && remainingCharges == 0
                        && reservation.isEmpty()
                        && recoversAt.isPresent();
        if (!availableShape && !reservedShape && !recoveryShape) {
            throw new IllegalArgumentException("resource node slot shape does not match its phase");
        }
    }

    public static ResourceNodeSlot available(int charges) {
        return new ResourceNodeSlot(
                ResourceNodePhase.AVAILABLE, charges, Optional.empty(), Optional.empty());
    }
}
