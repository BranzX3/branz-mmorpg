package com.branz.mmorpg.progression.teaching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.progression.knowledge.KnowledgeKey;
import com.branz.mmorpg.progression.knowledge.KnowledgeProfile;
import com.branz.mmorpg.progression.knowledge.KnowledgeType;
import com.branz.mmorpg.progression.knowledge.LearningRequirements;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeachingSessionEngineTest {
    private static final long START_TICK = 100;
    private static final KnowledgeKey TECHNIQUE =
            new KnowledgeKey(
                    KnowledgeType.TECHNIQUE, DefinitionId.of("technique.greatsword.cleaving_arc"));
    private static final DefinitionId MOVE = DefinitionId.of("move.greatsword.cleaving_arc");

    private final TeachingSessionEngine engine = new TeachingSessionEngine();
    private final CharacterId teacher = character();
    private final CharacterId student = character();

    @Test
    void completesAfterDemonstrationAndThreeUniqueSuccessfulExecutions() {
        TeachingSession session = successful(start());
        assertEquals(TeachingPhase.DEMONSTRATION, session.phase());
        assertEquals(
                START_TICK + TeachingSessionEngine.SESSION_DURATION_TICKS, session.expiresTick());

        session = successful(engine.demonstrate(session, teacher, MOVE, START_TICK + 1));
        UUID firstAction = UUID.randomUUID();
        session =
                successful(
                        engine.observeStudentAction(
                                session, student, firstAction, MOVE, true, START_TICK + 2));
        TeachingSession duplicate =
                successful(
                        engine.observeStudentAction(
                                session, student, firstAction, MOVE, true, START_TICK + 3));
        assertEquals(1, duplicate.successfulActionIds().size());
        session =
                successful(
                        engine.observeStudentAction(
                                duplicate, student, UUID.randomUUID(), MOVE, true, START_TICK + 4));
        session =
                successful(
                        engine.observeStudentAction(
                                session, student, UUID.randomUUID(), MOVE, true, START_TICK + 5));

        TeachingCompletion completion = successful(engine.completion(session, START_TICK + 6));
        assertEquals(TeachingPhase.READY_TO_COMMIT, session.phase());
        assertEquals(session.sessionId(), completion.teachingSessionId());
        assertEquals(TECHNIQUE, completion.learnedTechnique());
    }

    @Test
    void validatesTeacherOwnershipReadinessAndStudentPrerequisitesBeforeStart() {
        Result<TeachingSession, TeachingErrorCode> missingTeacher =
                engine.start(
                        UUID.randomUUID(),
                        teacher,
                        student,
                        TECHNIQUE,
                        LearningRequirements.none(),
                        emptyProfile(),
                        true,
                        true,
                        true,
                        emptyProfile(),
                        START_TICK);
        Result<TeachingSession, TeachingErrorCode> unreadyTeacher =
                engine.start(
                        UUID.randomUUID(),
                        teacher,
                        student,
                        TECHNIQUE,
                        LearningRequirements.none(),
                        teacherProfile(),
                        false,
                        true,
                        true,
                        emptyProfile(),
                        START_TICK);
        KnowledgeKey foundation =
                new KnowledgeKey(
                        KnowledgeType.FOUNDATION, DefinitionId.of("foundation.greatsword"));
        Result<TeachingSession, TeachingErrorCode> ineligibleStudent =
                engine.start(
                        UUID.randomUUID(),
                        teacher,
                        student,
                        TECHNIQUE,
                        new LearningRequirements(Set.of(foundation), Map.of(), Set.of()),
                        teacherProfile(),
                        true,
                        true,
                        true,
                        emptyProfile(),
                        START_TICK);

        assertFailure(missingTeacher, TeachingErrorCode.TEACHER_MISSING_KNOWLEDGE);
        assertFailure(unreadyTeacher, TeachingErrorCode.TEACHER_NOT_READY);
        assertFailure(ineligibleStudent, TeachingErrorCode.STUDENT_NOT_ELIGIBLE);
    }

    @Test
    void rejectsWrongActorWrongMoveAndUnsuccessfulExecution() {
        TeachingSession session = successful(start());
        Result<TeachingSession, TeachingErrorCode> wrongDemonstrator =
                engine.demonstrate(session, student, MOVE, START_TICK + 1);
        session = successful(engine.demonstrate(session, teacher, MOVE, START_TICK + 1));
        Result<TeachingSession, TeachingErrorCode> wrongMove =
                engine.observeStudentAction(
                        session,
                        student,
                        UUID.randomUUID(),
                        DefinitionId.of("move.greatsword.heavy_strike"),
                        true,
                        START_TICK + 2);
        Result<TeachingSession, TeachingErrorCode> failedAction =
                engine.observeStudentAction(
                        session, student, UUID.randomUUID(), MOVE, false, START_TICK + 2);

        assertFailure(wrongDemonstrator, TeachingErrorCode.WRONG_ACTOR);
        assertFailure(wrongMove, TeachingErrorCode.CHALLENGE_ACTION_INVALID);
        assertFailure(failedAction, TeachingErrorCode.CHALLENGE_ACTION_INVALID);
    }

    @Test
    void disconnectOrExpiryCancelsWithoutACompletionIntent() {
        TeachingSession disconnected =
                engine.cancelForDisconnect(successful(start()), student, START_TICK + 1);
        TeachingSession expired =
                engine.expire(
                        successful(start()),
                        START_TICK + TeachingSessionEngine.SESSION_DURATION_TICKS);

        assertEquals(
                TeachingCancellationReason.STUDENT_DISCONNECTED, disconnected.cancellationReason());
        assertEquals(TeachingCancellationReason.EXPIRED, expired.cancellationReason());
        assertFailure(
                engine.completion(disconnected, START_TICK + 2),
                TeachingErrorCode.SESSION_CANCELLED);
        assertFailure(
                engine.completion(
                        expired, START_TICK + TeachingSessionEngine.SESSION_DURATION_TICKS),
                TeachingErrorCode.SESSION_CANCELLED);
    }

    @Test
    void refusesSelfTeachingAndNonTechniqueKnowledge() {
        Result<TeachingSession, TeachingErrorCode> selfTeaching =
                engine.start(
                        UUID.randomUUID(),
                        teacher,
                        teacher,
                        TECHNIQUE,
                        LearningRequirements.none(),
                        teacherProfile(),
                        true,
                        true,
                        true,
                        emptyProfile(),
                        START_TICK);
        KnowledgeKey recipe =
                new KnowledgeKey(KnowledgeType.RECIPE, DefinitionId.of("recipe.flask.basic"));
        Result<TeachingSession, TeachingErrorCode> recipeTeaching =
                engine.start(
                        UUID.randomUUID(),
                        teacher,
                        student,
                        recipe,
                        LearningRequirements.none(),
                        new KnowledgeProfile(Set.of(recipe), Map.of(), Set.of()),
                        true,
                        true,
                        true,
                        emptyProfile(),
                        START_TICK);
        Result<TeachingSession, TeachingErrorCode> offlineTeacher =
                engine.start(
                        UUID.randomUUID(),
                        teacher,
                        student,
                        TECHNIQUE,
                        LearningRequirements.none(),
                        teacherProfile(),
                        true,
                        false,
                        true,
                        emptyProfile(),
                        START_TICK);

        assertFailure(selfTeaching, TeachingErrorCode.INVALID_PARTICIPANT);
        assertFailure(recipeTeaching, TeachingErrorCode.INVALID_TEACHING_TARGET);
        assertFailure(offlineTeacher, TeachingErrorCode.PARTICIPANT_OFFLINE);
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.cancelForDisconnect(successful(start()), character(), START_TICK + 1));
    }

    private Result<TeachingSession, TeachingErrorCode> start() {
        return engine.start(
                UUID.randomUUID(),
                teacher,
                student,
                TECHNIQUE,
                LearningRequirements.none(),
                teacherProfile(),
                true,
                true,
                true,
                emptyProfile(),
                START_TICK);
    }

    private static KnowledgeProfile teacherProfile() {
        return new KnowledgeProfile(Set.of(TECHNIQUE), Map.of(), Set.of());
    }

    private static KnowledgeProfile emptyProfile() {
        return new KnowledgeProfile(Set.of(), Map.of(), Set.of());
    }

    private static CharacterId character() {
        return new CharacterId(UUID.randomUUID());
    }

    private static <T> T successful(Result<T, TeachingErrorCode> result) {
        assertTrue(result instanceof Result.Success<T, TeachingErrorCode>);
        return ((Result.Success<T, TeachingErrorCode>) result).value();
    }

    private static void assertFailure(
            Result<?, TeachingErrorCode> result, TeachingErrorCode expected) {
        assertFalse(result.isSuccess());
        assertEquals(expected, ((Result.Failure<?, TeachingErrorCode>) result).error());
    }
}
