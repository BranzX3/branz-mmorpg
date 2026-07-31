package com.branz.mmorpg.items.projection;

import java.util.List;
import java.util.Objects;

/** Deterministic repair actions; removals must be applied before materializations. */
public record ProjectionReconciliationPlan(
        List<Integer> keepSlots, List<Integer> removeSlots, List<ExpectedProjection> materialize) {
    public ProjectionReconciliationPlan {
        keepSlots = List.copyOf(Objects.requireNonNull(keepSlots, "keepSlots"));
        removeSlots = List.copyOf(Objects.requireNonNull(removeSlots, "removeSlots"));
        materialize = List.copyOf(Objects.requireNonNull(materialize, "materialize"));
    }

    public boolean changed() {
        return !removeSlots.isEmpty() || !materialize.isEmpty();
    }
}
