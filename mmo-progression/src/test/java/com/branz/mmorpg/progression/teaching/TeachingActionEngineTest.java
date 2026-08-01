package com.branz.mmorpg.progression.teaching;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class TeachingActionEngineTest {
    private static final DefinitionId MOVE = DefinitionId.of("move.greatsword.cleave");
    private static final DefinitionId OTHER_MOVE = DefinitionId.of("move.greatsword.jab");
    private static final KnowledgeKey TECHNIQUE =
            new KnowledgeKey(
                    KnowledgeType.TECHNIQUE, DefinitionId.of("technique.greatsword.cleave"));

    private final TeachingSessionEngine sessions = new TeachingSessionEngine();
    private final TeachingActionEngine actions = new TeachingActionEngine(sessions);
    private final CharacterId teacher = character();
    private final CharacterId student = character();

    @Test
    void routesOnlyTheAuthoredMoveAndCorrectParticipant() {
        TeachingSession session = start();

        session = observe(session, student, UUID.randomUUID(), MOVE, 101);
        session = observe(session, teacher, UUID.randomUUID(), OTHER_MOVE, 102);
        assertEquals(TeachingPhase.DEMONSTRATION, session.phase());

        session = observe(session, teacher, UUID.randomUUID(), MOVE, 103);
        assertEquals(TeachingPhase.STUDENT_CHALLENGE, session.phase());
        session = observe(session, teacher, UUID.randomUUID(), MOVE, 104);
        assertEquals(0, session.successfulActionIds().size());

        UUID first = UUID.randomUUID();
        session = observe(session, student, first, MOVE, 105);
        session = observe(session, student, first, MOVE, 106);
        session = observe(session, student, UUID.randomUUID(), OTHER_MOVE, 107);
        assertEquals(1, session.successfulActionIds().size());
        session = observe(session, student, UUID.randomUUID(), MOVE, 108);
        session = observe(session, student, UUID.randomUUID(), MOVE, 109);

        assertEquals(TeachingPhase.READY_TO_COMMIT, session.phase());
        assertTrue(sessions.completion(session, 110).isSuccess());
    }

    private TeachingSession start() {
        Result<TeachingSession, TeachingErrorCode> result =
                sessions.start(
                        UUID.randomUUID(),
                        teacher,
                        student,
                        TECHNIQUE,
                        LearningRequirements.none(),
                        new KnowledgeProfile(Set.of(TECHNIQUE), Map.of(), Set.of()),
                        true,
                        true,
                        true,
                        new KnowledgeProfile(Set.of(), Map.of(), Set.of()),
                        100);
        assertTrue(result.isSuccess());
        return ((Result.Success<TeachingSession, TeachingErrorCode>) result).value();
    }

    private TeachingSession observe(
            TeachingSession session,
            CharacterId actor,
            UUID actionId,
            DefinitionId move,
            long tick) {
        Result<TeachingSession, TeachingErrorCode> result =
                actions.observeSuccessfulAction(session, MOVE, actor, actionId, move, tick);
        assertTrue(result.isSuccess());
        return ((Result.Success<TeachingSession, TeachingErrorCode>) result).value();
    }

    private static CharacterId character() {
        return new CharacterId(UUID.randomUUID());
    }
}
