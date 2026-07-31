package com.branz.mmorpg.scenes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SceneSession(
        SceneSessionId sessionId,
        UUID playerId,
        SceneMode mode,
        ScenePreviewState committedState,
        ScenePreviewState previewState,
        long revision,
        Instant openedAt) {
    public SceneSession {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(committedState, "committedState");
        Objects.requireNonNull(previewState, "previewState");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        Objects.requireNonNull(openedAt, "openedAt");
    }

    public boolean hasUncommittedPreview() {
        return !committedState.equals(previewState);
    }

    SceneSession withMode(SceneMode nextMode) {
        return new SceneSession(
                sessionId,
                playerId,
                nextMode,
                committedState,
                previewState,
                revision + 1,
                openedAt);
    }

    SceneSession withPreview(ScenePreviewState nextPreview) {
        return new SceneSession(
                sessionId, playerId, mode, committedState, nextPreview, revision + 1, openedAt);
    }

    SceneSession discardModePreview() {
        return new SceneSession(
                sessionId,
                playerId,
                SceneMode.HUB,
                committedState,
                committedState,
                revision + 1,
                openedAt);
    }

    SceneSession committed(ScenePreviewState nextCommitted) {
        return new SceneSession(
                sessionId, playerId, mode, nextCommitted, nextCommitted, revision + 1, openedAt);
    }
}
