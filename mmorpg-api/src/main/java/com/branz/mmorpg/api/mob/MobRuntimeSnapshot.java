package com.branz.mmorpg.api.mob;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Durable authoritative state for a placed mob. */
public record MobRuntimeSnapshot(
        UUID instanceId,
        ContentId definitionId,
        int level,
        SpatialPosition home,
        SpatialPosition position,
        MobAiState state,
        Optional<UUID> targetId,
        double health,
        double maximumHealth,
        Instant stateSince,
        Instant nextDecisionAt,
        Instant nextPathRequestAt,
        long decisionSequence,
        long rewardSequence) {
    public MobRuntimeSnapshot {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(stateSince, "stateSince");
        Objects.requireNonNull(nextDecisionAt, "nextDecisionAt");
        Objects.requireNonNull(nextPathRequestAt, "nextPathRequestAt");
        if (level < 1 || !Double.isFinite(health) || health < 0
                || !Double.isFinite(maximumHealth) || maximumHealth <= 0
                || health > maximumHealth || decisionSequence < 0 || rewardSequence < 0) {
            throw new IllegalArgumentException("invalid mob runtime snapshot");
        }
    }

    public static MobRuntimeSnapshot spawn(
            UUID instanceId, ContentId definitionId, int level,
            SpatialPosition home, double maximumHealth, Instant now) {
        return new MobRuntimeSnapshot(instanceId, definitionId, level, home, home,
                MobAiState.IDLE, Optional.empty(), maximumHealth, maximumHealth,
                now, now, now, 0, 0);
    }
}
