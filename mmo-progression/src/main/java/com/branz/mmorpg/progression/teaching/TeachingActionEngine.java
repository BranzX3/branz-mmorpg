package com.branz.mmorpg.progression.teaching;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import java.util.Objects;
import java.util.UUID;

/** Routes successful server-resolved combat actions into one teaching challenge. */
public final class TeachingActionEngine {
    private final TeachingSessionEngine sessions;

    public TeachingActionEngine() {
        this(new TeachingSessionEngine());
    }

    public TeachingActionEngine(TeachingSessionEngine sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    public Result<TeachingSession, TeachingErrorCode> observeSuccessfulAction(
            TeachingSession session,
            DefinitionId taughtMove,
            CharacterId actorId,
            UUID actionId,
            DefinitionId observedMove,
            long currentTick) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(taughtMove, "taughtMove");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(observedMove, "observedMove");
        if (!taughtMove.equals(observedMove)) {
            return Result.success(session);
        }
        return switch (session.phase()) {
            case DEMONSTRATION ->
                    actorId.equals(session.teacherId())
                            ? sessions.demonstrate(session, actorId, observedMove, currentTick)
                            : Result.success(session);
            case STUDENT_CHALLENGE ->
                    actorId.equals(session.studentId())
                            ? sessions.observeStudentAction(
                                    session, actorId, actionId, observedMove, true, currentTick)
                            : Result.success(session);
            case READY_TO_COMMIT -> Result.success(session);
            case CANCELLED ->
                    Result.failure(
                            TeachingErrorCode.SESSION_CANCELLED,
                            "Teaching was cancelled: " + session.cancellationReason() + ".");
        };
    }
}
