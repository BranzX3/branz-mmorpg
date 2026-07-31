package com.branz.mmorpg.scenes;

import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.equipment.EquipmentLoadout;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.items.quiver.QuiverPreparation;
import com.branz.mmorpg.progression.build.CharacterBuild;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** In-memory preview state only. Persistent changes are delegated exclusively to SceneCommitter. */
public final class SceneSessionManager {
    private final Clock clock;
    private final Map<UUID, SceneSession> sessions = new LinkedHashMap<>();

    public SceneSessionManager(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized Result<SceneSession, SceneErrorCode> open(
            UUID playerId, EquipmentLoadout committedEquipment) {
        return open(
                playerId, committedEquipment, QuiverPreparation.empty(), CharacterBuild.initial());
    }

    public synchronized Result<SceneSession, SceneErrorCode> open(
            UUID playerId,
            EquipmentLoadout committedEquipment,
            QuiverPreparation committedQuiverPreparation) {
        return open(
                playerId, committedEquipment, committedQuiverPreparation, CharacterBuild.initial());
    }

    public synchronized Result<SceneSession, SceneErrorCode> open(
            UUID playerId,
            EquipmentLoadout committedEquipment,
            QuiverPreparation committedQuiverPreparation,
            CharacterBuild committedBuild) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(committedEquipment, "committedEquipment");
        Objects.requireNonNull(committedQuiverPreparation, "committedQuiverPreparation");
        Objects.requireNonNull(committedBuild, "committedBuild");
        if (sessions.containsKey(playerId)) {
            return Result.failure(
                    SceneErrorCode.SCENE_ALREADY_OPEN, "Player already owns a Scene session.");
        }
        ScenePreviewState initial =
                new ScenePreviewState(
                        committedEquipment, committedQuiverPreparation, committedBuild);
        SceneSession session =
                new SceneSession(
                        SceneSessionId.random(),
                        playerId,
                        SceneMode.HUB,
                        initial,
                        initial,
                        0,
                        clock.instant());
        sessions.put(playerId, session);
        return Result.success(session);
    }

    public synchronized Optional<SceneSession> find(UUID playerId) {
        return Optional.ofNullable(sessions.get(Objects.requireNonNull(playerId, "playerId")));
    }

    public synchronized Result<SceneSession, SceneErrorCode> changeMode(
            UUID playerId, SceneSessionId sessionId, SceneMode mode) {
        Objects.requireNonNull(mode, "mode");
        return replace(playerId, sessionId, session -> session.withMode(mode));
    }

    public synchronized Result<SceneSession, SceneErrorCode> previewEquipment(
            UUID playerId, SceneSessionId sessionId, EquipmentSlot slot, Optional<ItemId> itemId) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(itemId, "itemId");
        return replace(
                playerId,
                sessionId,
                session ->
                        session.withPreview(
                                new ScenePreviewState(
                                        session.previewState().equipment().with(slot, itemId),
                                        session.previewState().quiverPreparation(),
                                        session.previewState().quiverTransfer(),
                                        session.previewState().build())));
    }

    public synchronized Result<SceneSession, SceneErrorCode> previewQuiverPreparation(
            UUID playerId, SceneSessionId sessionId, QuiverPreparation quiverPreparation) {
        Objects.requireNonNull(quiverPreparation, "quiverPreparation");
        return replace(
                playerId,
                sessionId,
                session ->
                        session.withPreview(
                                new ScenePreviewState(
                                        session.previewState().equipment(),
                                        quiverPreparation,
                                        session.previewState().quiverTransfer(),
                                        session.previewState().build())));
    }

    public synchronized Result<SceneSession, SceneErrorCode> previewQuiverTransfer(
            UUID playerId, SceneSessionId sessionId, QuiverAmmoTransferPreview transfer) {
        Objects.requireNonNull(transfer, "transfer");
        return replace(
                playerId,
                sessionId,
                session ->
                        session.withPreview(
                                new ScenePreviewState(
                                        session.previewState().equipment(),
                                        session.previewState().quiverPreparation(),
                                        Optional.of(transfer),
                                        session.previewState().build())));
    }

    public synchronized Result<SceneSession, SceneErrorCode> previewBuild(
            UUID playerId, SceneSessionId sessionId, CharacterBuild build) {
        Objects.requireNonNull(build, "build");
        return replace(
                playerId,
                sessionId,
                session ->
                        session.withPreview(
                                new ScenePreviewState(
                                        session.previewState().equipment(),
                                        session.previewState().quiverPreparation(),
                                        session.previewState().quiverTransfer(),
                                        build)));
    }

    public synchronized Result<SceneSession, SceneErrorCode> back(
            UUID playerId, SceneSessionId sessionId) {
        return replace(playerId, sessionId, SceneSession::discardModePreview);
    }

    public synchronized Result<SceneSession, SceneErrorCode> confirm(
            UUID playerId, SceneSessionId sessionId, SceneCommitter committer) {
        Objects.requireNonNull(committer, "committer");
        Result<SceneSession, SceneErrorCode> current = current(playerId, sessionId);
        if (!current.isSuccess()) {
            return current;
        }
        SceneSession session = ((Result.Success<SceneSession, SceneErrorCode>) current).value();
        Result<ScenePreviewState, SceneErrorCode> committed = committer.commit(session);
        if (committed instanceof Result.Failure<ScenePreviewState, SceneErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        ScenePreviewState state =
                ((Result.Success<ScenePreviewState, SceneErrorCode>) committed).value();
        SceneSession updated = session.committed(state);
        sessions.put(playerId, updated);
        return Result.success(updated);
    }

    public synchronized Result<ClosedSceneSession, SceneErrorCode> close(
            UUID playerId, SceneSessionId sessionId, SceneCloseReason reason) {
        Objects.requireNonNull(reason, "reason");
        Result<SceneSession, SceneErrorCode> current = current(playerId, sessionId);
        if (!current.isSuccess()) {
            Result.Failure<SceneSession, SceneErrorCode> failure =
                    (Result.Failure<SceneSession, SceneErrorCode>) current;
            return Result.failure(failure.error(), failure.detail());
        }
        SceneSession removed = sessions.remove(playerId);
        return Result.success(new ClosedSceneSession(removed, reason));
    }

    public synchronized Optional<ClosedSceneSession> closeCurrent(
            UUID playerId, SceneCloseReason reason) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(reason, "reason");
        SceneSession removed = sessions.remove(playerId);
        return removed == null
                ? Optional.empty()
                : Optional.of(new ClosedSceneSession(removed, reason));
    }

    public synchronized int closeAll(SceneCloseReason reason) {
        Objects.requireNonNull(reason, "reason");
        int count = sessions.size();
        sessions.clear();
        return count;
    }

    private Result<SceneSession, SceneErrorCode> replace(
            UUID playerId,
            SceneSessionId sessionId,
            java.util.function.UnaryOperator<SceneSession> update) {
        Result<SceneSession, SceneErrorCode> current = current(playerId, sessionId);
        if (!current.isSuccess()) {
            return current;
        }
        SceneSession updated =
                update.apply(((Result.Success<SceneSession, SceneErrorCode>) current).value());
        sessions.put(playerId, updated);
        return Result.success(updated);
    }

    private Result<SceneSession, SceneErrorCode> current(UUID playerId, SceneSessionId sessionId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sessionId, "sessionId");
        SceneSession current = sessions.get(playerId);
        if (current == null) {
            return Result.failure(
                    SceneErrorCode.SCENE_NOT_FOUND, "Player has no active Scene session.");
        }
        if (!current.sessionId().equals(sessionId)) {
            return Result.failure(
                    SceneErrorCode.SCENE_STALE_SESSION,
                    "Scene callback belongs to an older session.");
        }
        return Result.success(current);
    }
}
