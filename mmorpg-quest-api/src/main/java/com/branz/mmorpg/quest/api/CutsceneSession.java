package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CutsceneSession(
        UUID sessionId,
        ContentId cutsceneId,
        int definitionVersion,
        Set<UUID> participantSnapshot,
        State state,
        long elapsedMillis,
        long lastMonotonicNanos,
        int nextActionIndex,
        Set<String> appliedActionIds,
        Set<UUID> actorIds,
        boolean cameraAttached,
        boolean inputFrozen,
        boolean invulnerable,
        Instant startedAt,
        Instant updatedAt) {
    public enum State {
        PREPARING, PLAYING, PAUSED, SKIPPING,
        COMPLETING, CLEANING, COMPLETE, FAILED
    }
    public CutsceneSession {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(cutsceneId, "cutsceneId");
        participantSnapshot = Set.copyOf(participantSnapshot);
        Objects.requireNonNull(state, "state");
        appliedActionIds = Set.copyOf(appliedActionIds);
        actorIds = Set.copyOf(actorIds);
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (definitionVersion < 1 || participantSnapshot.isEmpty()
                || elapsedMillis < 0 || lastMonotonicNanos < 0 || nextActionIndex < 0) {
            throw new IllegalArgumentException("invalid cutscene session");
        }
    }
}
