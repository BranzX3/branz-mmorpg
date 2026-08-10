package com.branz.mmorpg.items.projection;

import java.util.Objects;
import java.util.Optional;

public record ProjectionMovePlan(
        ProjectionMoveDisposition disposition, Optional<ProjectionMoveIntent> intent) {
    public ProjectionMovePlan {
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(intent, "intent");
        if ((disposition == ProjectionMoveDisposition.READY_TO_COMMIT) != intent.isPresent()) {
            throw new IllegalArgumentException(
                    "only READY_TO_COMMIT projection plans carry a durable move intent");
        }
    }

    public static ProjectionMovePlan unchanged() {
        return new ProjectionMovePlan(ProjectionMoveDisposition.UNCHANGED, Optional.empty());
    }

    public static ProjectionMovePlan transientCursor() {
        return new ProjectionMovePlan(ProjectionMoveDisposition.TRANSIENT_CURSOR, Optional.empty());
    }

    public static ProjectionMovePlan ready(ProjectionMoveIntent intent) {
        return new ProjectionMovePlan(
                ProjectionMoveDisposition.READY_TO_COMMIT, Optional.of(intent));
    }
}
