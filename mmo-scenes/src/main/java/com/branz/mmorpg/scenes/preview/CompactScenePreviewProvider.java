package com.branz.mmorpg.scenes.preview;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.scenes.SceneErrorCode;
import com.branz.mmorpg.scenes.SceneSession;
import java.util.Objects;

/** Packet-free fallback that keeps preview state in the inventory UI only. */
public final class CompactScenePreviewProvider implements ScenePreviewProvider {
    @Override
    public Result<ScenePreviewHandle, SceneErrorCode> open(SceneSession session) {
        Objects.requireNonNull(session, "session");
        return Result.success(
                new ScenePreviewHandle(
                        session.sessionId(), session.playerId(), ScenePreviewMode.COMPACT_2D));
    }

    @Override
    public Result<ScenePreviewHandle, SceneErrorCode> update(
            ScenePreviewHandle handle, SceneSession session) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(session, "session");
        if (!handle.sessionId().equals(session.sessionId())
                || !handle.viewerId().equals(session.playerId())) {
            return Result.failure(
                    SceneErrorCode.SCENE_STALE_SESSION,
                    "Preview handle belongs to another Scene session.");
        }
        return Result.success(handle);
    }

    @Override
    public void close(ScenePreviewHandle handle) {
        Objects.requireNonNull(handle, "handle");
    }
}
