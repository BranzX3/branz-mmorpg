package com.branz.mmorpg.scenes;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.scenes.actor.PreviewActorHandle;
import com.branz.mmorpg.scenes.actor.PreviewActorProvider;
import com.branz.mmorpg.scenes.environment.SceneEnvironmentHandle;
import com.branz.mmorpg.scenes.environment.SceneEnvironmentProvider;
import com.branz.mmorpg.scenes.overlay.MenuOverlay;
import com.branz.mmorpg.scenes.overlay.MenuOverlayHandle;
import com.branz.mmorpg.scenes.viewpoint.SceneViewpointHandle;
import com.branz.mmorpg.scenes.viewpoint.SceneViewpointProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Provider-neutral presentation orchestrator. Domain state and durable commits stay in their owning
 * feature; this engine owns only environment, actor, viewpoint, overlay and recovery lifecycles.
 */
public final class SceneEngine {
    private final SceneEnvironmentProvider environments;
    private final PreviewActorProvider actors;
    private final SceneViewpointProvider viewpoints;
    private final MenuOverlay overlays;
    private final SceneRecovery recovery;
    private final Map<SceneSessionId, ScenePresentation> presentations = new HashMap<>();

    public SceneEngine(
            SceneEnvironmentProvider environments,
            PreviewActorProvider actors,
            SceneViewpointProvider viewpoints,
            MenuOverlay overlays,
            SceneRecovery recovery) {
        this.environments = Objects.requireNonNull(environments, "environments");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.viewpoints = Objects.requireNonNull(viewpoints, "viewpoints");
        this.overlays = Objects.requireNonNull(overlays, "overlays");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
    }

    public synchronized Result<ScenePresentation, SceneErrorCode> open(SceneSession session) {
        Objects.requireNonNull(session, "session");
        if (presentations.containsKey(session.sessionId())) {
            return Result.failure(
                    SceneErrorCode.SCENE_ALREADY_OPEN,
                    "Scene presentation is already open for this session.");
        }
        SceneEnvironmentHandle environment = success(environments.open(session));
        if (environment == null) {
            return providerFailure("environment");
        }
        PreviewActorHandle actor = success(actors.open(session));
        if (actor == null) {
            safeClose(() -> environments.close(environment));
            return providerFailure("preview actor");
        }
        SceneViewpointHandle viewpoint = success(viewpoints.open(session, actor));
        if (viewpoint == null) {
            safeClose(() -> actors.close(actor));
            safeClose(() -> environments.close(environment));
            return providerFailure("viewpoint");
        }
        MenuOverlayHandle overlay = success(overlays.open(session));
        if (overlay == null) {
            safeClose(() -> viewpoints.close(viewpoint));
            safeClose(() -> actors.close(actor));
            safeClose(() -> environments.close(environment));
            return providerFailure("menu overlay");
        }
        ScenePresentation presentation =
                new ScenePresentation(environment, actor, viewpoint, overlay);
        presentations.put(session.sessionId(), presentation);
        return Result.success(presentation);
    }

    public synchronized Result<ScenePresentation, SceneErrorCode> update(SceneSession session) {
        Objects.requireNonNull(session, "session");
        ScenePresentation current = presentations.get(session.sessionId());
        if (current == null) {
            return Result.failure(
                    SceneErrorCode.SCENE_NOT_FOUND, "Scene presentation is not open.");
        }
        PreviewActorHandle actor = success(actors.update(current.actor(), session));
        MenuOverlayHandle overlay = success(overlays.update(current.overlay(), session));
        if (actor == null || overlay == null) {
            close(session.sessionId());
            return providerFailure(actor == null ? "preview actor update" : "overlay update");
        }
        ScenePresentation updated =
                new ScenePresentation(current.environment(), actor, current.viewpoint(), overlay);
        presentations.put(session.sessionId(), updated);
        return Result.success(updated);
    }

    public synchronized boolean close(SceneSessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        ScenePresentation presentation = presentations.remove(sessionId);
        if (presentation == null) {
            return false;
        }
        recovery.recover(
                sessionId,
                List.of(
                        () -> overlays.close(presentation.overlay()),
                        () -> viewpoints.close(presentation.viewpoint()),
                        () -> actors.close(presentation.actor()),
                        () -> environments.close(presentation.environment())));
        return true;
    }

    public synchronized Optional<ScenePresentation> find(SceneSessionId sessionId) {
        return Optional.ofNullable(
                presentations.get(Objects.requireNonNull(sessionId, "sessionId")));
    }

    public synchronized int closeAll() {
        List<SceneSessionId> ids = List.copyOf(presentations.keySet());
        RuntimeException failure = null;
        for (SceneSessionId id : ids) {
            try {
                close(id);
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
        return ids.size();
    }

    private static <T> T success(Result<T, SceneErrorCode> result) {
        return result instanceof Result.Success<T, SceneErrorCode> success ? success.value() : null;
    }

    private static <T> Result<T, SceneErrorCode> providerFailure(String provider) {
        return Result.failure(
                SceneErrorCode.SCENE_PROVIDER_FAILURE, provider + " could not open or update.");
    }

    private static void safeClose(Runnable close) {
        try {
            close.run();
        } catch (RuntimeException ignored) {
            // The original provider failure is authoritative; best-effort cleanup continues.
        }
    }
}
