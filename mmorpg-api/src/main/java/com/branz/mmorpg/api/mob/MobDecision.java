package com.branz.mmorpg.api.mob;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MobDecision(
        MobRuntimeSnapshot snapshot,
        Action action,
        Optional<UUID> targetId,
        Optional<ContentId> skillId,
        boolean requestPath) {
    public enum Action { NONE, ACQUIRE, MOVE, CAST, RESET, DIE }

    public MobDecision {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(skillId, "skillId");
    }
}
