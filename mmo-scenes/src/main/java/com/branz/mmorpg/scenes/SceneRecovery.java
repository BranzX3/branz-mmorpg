package com.branz.mmorpg.scenes;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Idempotent reverse-order cleanup coordinator for partially or fully opened Scenes. */
public final class SceneRecovery {
    private final Set<SceneSessionId> recovering = new HashSet<>();
    private final Set<SceneSessionId> recovered = new HashSet<>();

    public synchronized boolean recover(SceneSessionId sessionId, List<Runnable> cleanupOrder) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(cleanupOrder, "cleanupOrder");
        if (recovering.contains(sessionId) || recovered.contains(sessionId)) {
            return false;
        }
        recovering.add(sessionId);
        RuntimeException failure = null;
        for (Runnable cleanup : cleanupOrder) {
            try {
                Objects.requireNonNull(cleanup, "cleanup").run();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        recovering.remove(sessionId);
        recovered.add(sessionId);
        if (failure != null) {
            throw failure;
        }
        return true;
    }

    public synchronized boolean recovered(SceneSessionId sessionId) {
        return recovered.contains(Objects.requireNonNull(sessionId, "sessionId"));
    }
}
