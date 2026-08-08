package com.branz.mmorpg.scenes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SceneSession(
        SceneSessionId sessionId,
        UUID playerId,
        SceneProfile profile,
        SceneMode mode,
        SceneLifecyclePhase phase,
        ScenePreviewState committedState,
        ScenePreviewState previewState,
        long revision,
        Instant openedAt) {
    public SceneSession {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(phase, "phase");
        if (profile.mode(mode).isEmpty()) {
            throw new IllegalArgumentException("Scene mode is not supported by profile");
        }
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
                profile,
                nextMode,
                SceneLifecyclePhase.ACTIVE,
                committedState,
                previewState,
                revision + 1,
                openedAt);
    }

    SceneSession withPreview(ScenePreviewState nextPreview) {
        return new SceneSession(
                sessionId,
                playerId,
                profile,
                mode,
                phase,
                committedState,
                nextPreview,
                revision + 1,
                openedAt);
    }

    SceneSession discardModePreview() {
        return new SceneSession(
                sessionId,
                playerId,
                profile,
                profile.entryMode(),
                SceneLifecyclePhase.ACTIVE,
                committedState,
                committedState,
                revision + 1,
                openedAt);
    }

    SceneSession committed(ScenePreviewState nextCommitted) {
        return new SceneSession(
                sessionId,
                playerId,
                profile,
                mode,
                SceneLifecyclePhase.ACTIVE,
                nextCommitted,
                nextCommitted,
                revision + 1,
                openedAt);
    }

    SceneSession withPhase(SceneLifecyclePhase nextPhase) {
        return new SceneSession(
                sessionId,
                playerId,
                profile,
                mode,
                nextPhase,
                committedState,
                previewState,
                revision + 1,
                openedAt);
    }

    public SceneModeProfile modeProfile() {
        return profile.requireMode(mode);
    }
}
