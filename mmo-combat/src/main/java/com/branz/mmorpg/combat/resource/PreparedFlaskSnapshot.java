package com.branz.mmorpg.combat.resource;

import java.util.Objects;
import java.util.UUID;

/** Flask state captured for one concrete boss-checkpoint expedition instance. */
public record PreparedFlaskSnapshot(UUID checkpointInstanceId, FlaskState flaskState) {
    public PreparedFlaskSnapshot {
        Objects.requireNonNull(checkpointInstanceId, "checkpointInstanceId");
        Objects.requireNonNull(flaskState, "flaskState");
    }
}
