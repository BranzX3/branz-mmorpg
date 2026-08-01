package com.branz.mmorpg.lifeskills.node;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ResourceNodeTransition(
        ResourceNodeRuntime runtime,
        boolean changed,
        Optional<ResourceNodeReservation> newReservation,
        Optional<ResourceNodeHarvestCommit> harvestCommit,
        Set<UUID> releasedReservations,
        Set<ResourceNodeAccessKey> recoveredSlots) {
    public ResourceNodeTransition {
        Objects.requireNonNull(runtime, "runtime");
        newReservation = Objects.requireNonNull(newReservation, "newReservation");
        harvestCommit = Objects.requireNonNull(harvestCommit, "harvestCommit");
        releasedReservations =
                Set.copyOf(Objects.requireNonNull(releasedReservations, "releasedReservations"));
        recoveredSlots = Set.copyOf(Objects.requireNonNull(recoveredSlots, "recoveredSlots"));
        if (!changed
                && (newReservation.isPresent()
                        || harvestCommit.isPresent()
                        || !releasedReservations.isEmpty()
                        || !recoveredSlots.isEmpty())) {
            throw new IllegalArgumentException("unchanged node transition cannot emit effects");
        }
    }

    public static ResourceNodeTransition unchanged(ResourceNodeRuntime runtime) {
        return new ResourceNodeTransition(
                runtime, false, Optional.empty(), Optional.empty(), Set.of(), Set.of());
    }
}
