package com.branz.mmorpg.api.mob;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record MobTargetCandidate(
        UUID entityId,
        SpatialPosition position,
        boolean alive,
        boolean targetable,
        double threat,
        Set<String> tags) {
    public MobTargetCandidate {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(position, "position");
        tags = Set.copyOf(tags);
        if (!Double.isFinite(threat) || threat < 0) {
            throw new IllegalArgumentException("invalid target threat");
        }
    }
}
