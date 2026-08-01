package com.branz.mmorpg.combat.resource;

import com.branz.mmorpg.api.result.Result;
import java.util.Objects;
import java.util.UUID;

/** Pure capture/restore policy; durable once-only behavior belongs to the transaction journal. */
public final class BossFlaskCheckpointEngine {
    public PreparedFlaskSnapshot capture(UUID checkpointInstanceId, FlaskState preparedState) {
        return new PreparedFlaskSnapshot(checkpointInstanceId, preparedState);
    }

    public Result<FlaskState, FlaskCheckpointErrorCode> restore(
            UUID activeCheckpointInstanceId,
            PreparedFlaskSnapshot snapshot,
            boolean confirmedFullWipe) {
        Objects.requireNonNull(activeCheckpointInstanceId, "activeCheckpointInstanceId");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!confirmedFullWipe) {
            return Result.failure(
                    FlaskCheckpointErrorCode.FLASK_WIPE_NOT_CONFIRMED,
                    "Only a confirmed full encounter wipe may restore the prepared Flask.");
        }
        if (!snapshot.checkpointInstanceId().equals(activeCheckpointInstanceId)) {
            return Result.failure(
                    FlaskCheckpointErrorCode.FLASK_CHECKPOINT_MISMATCH,
                    "Prepared Flask snapshot belongs to another checkpoint instance.");
        }
        return Result.success(snapshot.flaskState());
    }
}
