package com.branz.mmorpg.progression.teaching;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.progression.knowledge.KnowledgeKey;
import com.branz.mmorpg.progression.knowledge.KnowledgeLearningEngine;
import com.branz.mmorpg.progression.knowledge.KnowledgeProfile;
import com.branz.mmorpg.progression.knowledge.KnowledgeType;
import com.branz.mmorpg.progression.knowledge.LearningDecision;
import com.branz.mmorpg.progression.knowledge.LearningRequirements;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pure server-authoritative state machine for synchronous player teaching. */
public final class TeachingSessionEngine {
    public static final long SESSION_DURATION_TICKS = 12_000;
    public static final int REQUIRED_SUCCESSFUL_ACTIONS = 3;

    private final KnowledgeLearningEngine learningEngine = new KnowledgeLearningEngine();

    public Result<TeachingSession, TeachingErrorCode> start(
            UUID sessionId,
            CharacterId teacherId,
            CharacterId studentId,
            KnowledgeKey technique,
            LearningRequirements studentRequirements,
            KnowledgeProfile teacherProfile,
            boolean teacherReady,
            boolean teacherOnline,
            boolean studentOnline,
            KnowledgeProfile studentProfile,
            long currentTick) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(teacherId, "teacherId");
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(technique, "technique");
        Objects.requireNonNull(studentRequirements, "studentRequirements");
        Objects.requireNonNull(teacherProfile, "teacherProfile");
        Objects.requireNonNull(studentProfile, "studentProfile");
        requireTick(currentTick);
        if (teacherId.equals(studentId)) {
            return Result.failure(
                    TeachingErrorCode.INVALID_PARTICIPANT,
                    "Teacher and student must be different online characters.");
        }
        if (!teacherOnline || !studentOnline) {
            return Result.failure(
                    TeachingErrorCode.PARTICIPANT_OFFLINE,
                    "Teacher and student must both be online when teaching starts.");
        }
        if (technique.type() != KnowledgeType.TECHNIQUE) {
            return Result.failure(
                    TeachingErrorCode.INVALID_TEACHING_TARGET,
                    "V1 player teaching supports Technique knowledge only.");
        }
        if (!teacherProfile.knows(technique)) {
            return Result.failure(
                    TeachingErrorCode.TEACHER_MISSING_KNOWLEDGE,
                    "Teacher does not know " + technique.id().value() + ".");
        }
        if (!teacherReady) {
            return Result.failure(
                    TeachingErrorCode.TEACHER_NOT_READY,
                    "Teacher has not reached the authored teaching readiness.");
        }
        LearningDecision eligibility =
                learningEngine.evaluate(technique, studentRequirements, studentProfile);
        if (!eligibility.accepted()) {
            return Result.failure(
                    TeachingErrorCode.STUDENT_NOT_ELIGIBLE,
                    eligibility.reason()
                            + eligibility
                                    .missingRequirement()
                                    .map(value -> ": " + value)
                                    .orElse(""));
        }
        return Result.success(
                new TeachingSession(
                        sessionId,
                        teacherId,
                        studentId,
                        technique,
                        currentTick,
                        Math.addExact(currentTick, SESSION_DURATION_TICKS),
                        TeachingPhase.DEMONSTRATION,
                        Optional.empty(),
                        Set.of(),
                        TeachingCancellationReason.NONE));
    }

    public Result<TeachingSession, TeachingErrorCode> demonstrate(
            TeachingSession session,
            CharacterId actorId,
            DefinitionId demonstratedMove,
            long currentTick) {
        Objects.requireNonNull(demonstratedMove, "demonstratedMove");
        Result<TeachingSession, TeachingErrorCode> valid =
                requireActive(session, actorId, session.teacherId(), currentTick);
        if (!valid.isSuccess()) {
            return valid;
        }
        if (session.phase() != TeachingPhase.DEMONSTRATION) {
            return Result.failure(
                    TeachingErrorCode.INVALID_PHASE, "Teaching is not in demonstration phase.");
        }
        return Result.success(
                copy(
                        session,
                        TeachingPhase.STUDENT_CHALLENGE,
                        Optional.of(demonstratedMove),
                        session.successfulActionIds(),
                        TeachingCancellationReason.NONE));
    }

    public Result<TeachingSession, TeachingErrorCode> observeStudentAction(
            TeachingSession session,
            CharacterId actorId,
            UUID actionId,
            DefinitionId moveId,
            boolean successful,
            long currentTick) {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(moveId, "moveId");
        Result<TeachingSession, TeachingErrorCode> valid =
                requireActive(session, actorId, session.studentId(), currentTick);
        if (!valid.isSuccess()) {
            return valid;
        }
        if (session.phase() != TeachingPhase.STUDENT_CHALLENGE) {
            return Result.failure(
                    TeachingErrorCode.INVALID_PHASE, "Teaching is not in student challenge phase.");
        }
        if (!successful || !session.demonstratedMove().orElseThrow().equals(moveId)) {
            return Result.failure(
                    TeachingErrorCode.CHALLENGE_ACTION_INVALID,
                    "Only a successful execution of the demonstrated move advances the challenge.");
        }
        if (session.successfulActionIds().contains(actionId)) {
            return Result.success(session);
        }
        HashSet<UUID> actions = new HashSet<>(session.successfulActionIds());
        actions.add(actionId);
        TeachingPhase phase =
                actions.size() == REQUIRED_SUCCESSFUL_ACTIONS
                        ? TeachingPhase.READY_TO_COMMIT
                        : TeachingPhase.STUDENT_CHALLENGE;
        return Result.success(
                copy(
                        session,
                        phase,
                        session.demonstratedMove(),
                        actions,
                        TeachingCancellationReason.NONE));
    }

    public Result<TeachingCompletion, TeachingErrorCode> completion(
            TeachingSession session, long currentTick) {
        Objects.requireNonNull(session, "session");
        requireTick(currentTick);
        if (session.phase() == TeachingPhase.CANCELLED) {
            return Result.failure(
                    TeachingErrorCode.SESSION_CANCELLED,
                    "Teaching was cancelled: " + session.cancellationReason() + ".");
        }
        if (currentTick >= session.expiresTick()) {
            return Result.failure(TeachingErrorCode.SESSION_EXPIRED, "Teaching session expired.");
        }
        if (session.phase() != TeachingPhase.READY_TO_COMMIT) {
            return Result.failure(
                    TeachingErrorCode.INVALID_PHASE, "Student challenge is not complete.");
        }
        return Result.success(
                new TeachingCompletion(
                        session.sessionId(),
                        session.teacherId(),
                        session.studentId(),
                        session.technique()));
    }

    public TeachingSession cancelForDisconnect(
            TeachingSession session, CharacterId disconnectedCharacter, long currentTick) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(disconnectedCharacter, "disconnectedCharacter");
        requireTick(currentTick);
        if (session.phase() == TeachingPhase.CANCELLED) {
            return session;
        }
        TeachingCancellationReason reason;
        if (session.teacherId().equals(disconnectedCharacter)) {
            reason = TeachingCancellationReason.TEACHER_DISCONNECTED;
        } else if (session.studentId().equals(disconnectedCharacter)) {
            reason = TeachingCancellationReason.STUDENT_DISCONNECTED;
        } else {
            throw new IllegalArgumentException("disconnected character is not a participant");
        }
        return copy(
                session,
                TeachingPhase.CANCELLED,
                session.demonstratedMove(),
                session.successfulActionIds(),
                reason);
    }

    public TeachingSession expire(TeachingSession session, long currentTick) {
        Objects.requireNonNull(session, "session");
        requireTick(currentTick);
        if (session.phase() == TeachingPhase.CANCELLED || currentTick < session.expiresTick()) {
            return session;
        }
        return copy(
                session,
                TeachingPhase.CANCELLED,
                session.demonstratedMove(),
                session.successfulActionIds(),
                TeachingCancellationReason.EXPIRED);
    }

    private Result<TeachingSession, TeachingErrorCode> requireActive(
            TeachingSession session,
            CharacterId actorId,
            CharacterId expectedActor,
            long currentTick) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(actorId, "actorId");
        requireTick(currentTick);
        if (session.phase() == TeachingPhase.CANCELLED) {
            return Result.failure(
                    TeachingErrorCode.SESSION_CANCELLED,
                    "Teaching was cancelled: " + session.cancellationReason() + ".");
        }
        if (currentTick >= session.expiresTick()) {
            return Result.failure(TeachingErrorCode.SESSION_EXPIRED, "Teaching session expired.");
        }
        if (!expectedActor.equals(actorId)) {
            return Result.failure(
                    TeachingErrorCode.WRONG_ACTOR, "This action belongs to the other participant.");
        }
        return Result.success(session);
    }

    private static TeachingSession copy(
            TeachingSession session,
            TeachingPhase phase,
            Optional<DefinitionId> move,
            Set<UUID> actions,
            TeachingCancellationReason cancellationReason) {
        return new TeachingSession(
                session.sessionId(),
                session.teacherId(),
                session.studentId(),
                session.technique(),
                session.startedTick(),
                session.expiresTick(),
                phase,
                move,
                actions,
                cancellationReason);
    }

    private static void requireTick(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("currentTick must not be negative");
        }
    }
}
