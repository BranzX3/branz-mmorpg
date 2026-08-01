package com.branz.mmorpg.progression.teaching;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.progression.knowledge.KnowledgeKey;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record TeachingSession(
        UUID sessionId,
        CharacterId teacherId,
        CharacterId studentId,
        KnowledgeKey technique,
        long startedTick,
        long expiresTick,
        TeachingPhase phase,
        Optional<DefinitionId> demonstratedMove,
        Set<UUID> successfulActionIds,
        TeachingCancellationReason cancellationReason) {
    public TeachingSession {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(teacherId, "teacherId");
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(technique, "technique");
        Objects.requireNonNull(phase, "phase");
        demonstratedMove = Objects.requireNonNull(demonstratedMove, "demonstratedMove");
        successfulActionIds =
                Set.copyOf(Objects.requireNonNull(successfulActionIds, "successfulActionIds"));
        Objects.requireNonNull(cancellationReason, "cancellationReason");
        if (startedTick < 0 || expiresTick <= startedTick) {
            throw new IllegalArgumentException("teaching tick window must be positive");
        }
        if (teacherId.equals(studentId)) {
            throw new IllegalArgumentException("teacher and student must be different characters");
        }
        if (successfulActionIds.size() > TeachingSessionEngine.REQUIRED_SUCCESSFUL_ACTIONS) {
            throw new IllegalArgumentException(
                    "successful action set exceeds challenge requirement");
        }
        if ((phase == TeachingPhase.STUDENT_CHALLENGE || phase == TeachingPhase.READY_TO_COMMIT)
                && demonstratedMove.isEmpty()) {
            throw new IllegalArgumentException("challenge phase requires a demonstrated move");
        }
        if (phase == TeachingPhase.READY_TO_COMMIT
                && successfulActionIds.size()
                        != TeachingSessionEngine.REQUIRED_SUCCESSFUL_ACTIONS) {
            throw new IllegalArgumentException("ready phase requires a completed challenge");
        }
        if ((phase == TeachingPhase.CANCELLED)
                != (cancellationReason != TeachingCancellationReason.NONE)) {
            throw new IllegalArgumentException(
                    "only cancelled sessions have a cancellation reason");
        }
    }
}
