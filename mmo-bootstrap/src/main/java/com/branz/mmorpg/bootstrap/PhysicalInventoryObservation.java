package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.items.projection.ObservedProjection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable physical inventory projection captured after a player inventory interaction. */
record PhysicalInventoryObservation(
        List<ObservedProjection> storage, Optional<ObservedProjection> cursor) {
    PhysicalInventoryObservation {
        storage = List.copyOf(Objects.requireNonNull(storage, "storage"));
        Objects.requireNonNull(cursor, "cursor");
    }
}
