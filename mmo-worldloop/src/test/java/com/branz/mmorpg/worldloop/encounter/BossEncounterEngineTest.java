package com.branz.mmorpg.worldloop.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BossEncounterEngineTest {
    private static final long START_TICK = 100;
    private static final DefinitionId BOSS = DefinitionId.of("encounter.boss.training_golem");

    private final BossEncounterEngine engine = new BossEncounterEngine();
    private final CharacterId first = character();
    private final CharacterId second = character();

    @Test
    void partyWipeRequestsOneRestorePerLockedParticipantAndStartsNextAttempt() {
        BossEncounterRuntime runtime = start(first, second);
        runtime = successful(engine.defeat(runtime, first, operation(), START_TICK + 1)).runtime();
        assertEquals(BossEncounterPhase.ACTIVE, runtime.phase());

        runtime = successful(engine.defeat(runtime, second, operation(), START_TICK + 2)).runtime();
        assertEquals(BossEncounterPhase.WIPE_PENDING, runtime.phase());

        UUID resetOperation = operation();
        BossEncounterTransition reset = successful(engine.beginReset(runtime, resetOperation));
        assertEquals(BossEncounterPhase.RESETTING, reset.runtime().phase());
        assertEquals(runtime.participants().keySet(), reset.flaskRestoreParticipants());

        UUID completionOperation = operation();
        BossEncounterTransition completed =
                successful(
                        engine.completeReset(reset.runtime(), resetOperation, completionOperation));
        assertEquals(BossEncounterPhase.ACTIVE, completed.runtime().phase());
        assertEquals(2, completed.runtime().attempt());
        assertTrue(
                completed.runtime().participants().values().stream()
                        .allMatch(
                                participant ->
                                        participant.status() == EncounterParticipantStatus.ACTIVE));
    }

    @Test
    void duplicateResetOperationNeverEmitsASecondRestore() {
        BossEncounterRuntime wiped =
                successful(engine.defeat(start(first), first, operation(), START_TICK + 1))
                        .runtime();
        UUID resetOperation = operation();
        BossEncounterTransition firstReset = successful(engine.beginReset(wiped, resetOperation));
        BossEncounterTransition duplicate =
                successful(engine.beginReset(firstReset.runtime(), resetOperation));

        assertTrue(firstReset.changed());
        assertEquals(1, firstReset.flaskRestoreParticipants().size());
        assertFalse(duplicate.changed());
        assertTrue(duplicate.flaskRestoreParticipants().isEmpty());
    }

    @Test
    void connectedSurvivorPreventsPartyWipe() {
        BossEncounterRuntime runtime = start(first, second);
        runtime = successful(engine.defeat(runtime, first, operation(), START_TICK + 1)).runtime();

        assertEquals(BossEncounterPhase.ACTIVE, runtime.phase());
        assertEquals(
                EncounterParticipantStatus.ACTIVE, runtime.participants().get(second).status());
    }

    @Test
    void reconnectWithinGracePreservesAttemptAndDoesNotRestoreFlasks() {
        BossEncounterRuntime runtime = start(first, second);
        runtime =
                successful(engine.disconnect(runtime, first, operation(), START_TICK + 1))
                        .runtime();
        UUID advanceOperation = operation();
        BossEncounterTransition earlyAdvance =
                successful(
                        engine.advanceGrace(
                                runtime,
                                advanceOperation,
                                START_TICK + BossEncounterEngine.REJOIN_GRACE_TICKS));
        assertFalse(earlyAdvance.changed());

        BossEncounterTransition rejoined =
                successful(
                        engine.reconnect(
                                runtime,
                                first,
                                operation(),
                                START_TICK + BossEncounterEngine.REJOIN_GRACE_TICKS - 1));
        assertEquals(
                EncounterParticipantStatus.ACTIVE,
                rejoined.runtime().participants().get(first).status());
        assertEquals(1, rejoined.runtime().attempt());
        assertTrue(rejoined.flaskRestoreParticipants().isEmpty());
    }

    @Test
    void expiredGraceContributesToWipeOnlyAfterEveryParticipantIsUnavailable() {
        BossEncounterRuntime runtime = start(first, second);
        runtime =
                successful(engine.disconnect(runtime, first, operation(), START_TICK + 1))
                        .runtime();
        runtime = successful(engine.defeat(runtime, second, operation(), START_TICK + 2)).runtime();
        assertEquals(BossEncounterPhase.ACTIVE, runtime.phase());

        BossEncounterTransition expired =
                successful(
                        engine.advanceGrace(
                                runtime,
                                operation(),
                                START_TICK + BossEncounterEngine.REJOIN_GRACE_TICKS + 1));
        assertEquals(BossEncounterPhase.WIPE_PENDING, expired.runtime().phase());
    }

    @Test
    void victoryWinsRaceWithPendingWipeAndPermanentlyDisablesReset() {
        BossEncounterRuntime wiped =
                successful(engine.defeat(start(first), first, operation(), START_TICK + 1))
                        .runtime();
        UUID victoryOperation = operation();
        BossEncounterTransition victory =
                successful(engine.confirmVictory(wiped, victoryOperation));
        assertEquals(BossEncounterPhase.VICTORY_PENDING, victory.runtime().phase());
        assertTrue(victory.rewardReconciliationRequested());

        Result<BossEncounterTransition, BossEncounterErrorCode> reset =
                engine.beginReset(victory.runtime(), operation());
        assertFailure(BossEncounterErrorCode.INVALID_PHASE, reset);

        UUID grantId = operation();
        BossEncounterTransition completed =
                successful(engine.reconcileRewards(victory.runtime(), operation(), grantId));
        assertEquals(BossEncounterPhase.COMPLETED, completed.runtime().phase());
        assertEquals(grantId, completed.runtime().rewardGrantId().orElseThrow());
        assertFailure(
                BossEncounterErrorCode.INVALID_PHASE,
                engine.beginReset(completed.runtime(), operation()));
    }

    @Test
    void repeatedVictoryAndRewardOperationsAreIdempotent() {
        BossEncounterRuntime active = start(first);
        UUID victoryOperation = operation();
        BossEncounterTransition victory =
                successful(engine.confirmVictory(active, victoryOperation));
        BossEncounterTransition duplicateVictory =
                successful(engine.confirmVictory(victory.runtime(), victoryOperation));
        assertFalse(duplicateVictory.changed());
        assertFalse(duplicateVictory.rewardReconciliationRequested());

        UUID rewardOperation = operation();
        BossEncounterTransition completed =
                successful(
                        engine.reconcileRewards(victory.runtime(), rewardOperation, operation()));
        BossEncounterTransition duplicateReward =
                successful(
                        engine.reconcileRewards(completed.runtime(), rewardOperation, operation()));
        assertFalse(duplicateReward.changed());
        assertEquals(completed.runtime(), duplicateReward.runtime());
    }

    @Test
    void operationIdsCannotBeReusedForDifferentCommands() {
        BossEncounterRuntime runtime = start(first, second);
        UUID operationId = operation();
        runtime =
                successful(engine.disconnect(runtime, first, operationId, START_TICK + 1))
                        .runtime();

        assertFailure(
                BossEncounterErrorCode.OPERATION_ID_REUSED,
                engine.reconnect(runtime, first, operationId, START_TICK + 2));
    }

    @Test
    void validatesParticipantMembershipAndPartyLimit() {
        assertFailure(
                BossEncounterErrorCode.INVALID_PARTICIPANTS,
                engine.start(
                        new EncounterId(operation()), BOSS, operation(), List.of(), START_TICK));
        assertFailure(
                BossEncounterErrorCode.INVALID_PARTICIPANTS,
                engine.start(
                        new EncounterId(operation()),
                        BOSS,
                        operation(),
                        List.of(
                                character(),
                                character(),
                                character(),
                                character(),
                                character(),
                                character()),
                        START_TICK));

        BossEncounterRuntime runtime = start(first);
        assertFailure(
                BossEncounterErrorCode.PARTICIPANT_NOT_FOUND,
                engine.defeat(runtime, second, operation(), START_TICK + 1));
    }

    private BossEncounterRuntime start(CharacterId... participants) {
        return successful(
                engine.start(
                        new EncounterId(operation()),
                        BOSS,
                        operation(),
                        List.of(participants),
                        START_TICK));
    }

    private static CharacterId character() {
        return new CharacterId(operation());
    }

    private static UUID operation() {
        return UUID.randomUUID();
    }

    private static <T> T successful(Result<T, BossEncounterErrorCode> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<T, BossEncounterErrorCode>) result).value();
    }

    private static void assertFailure(
            BossEncounterErrorCode expected, Result<?, BossEncounterErrorCode> result) {
        assertFalse(result.isSuccess());
        assertEquals(expected, ((Result.Failure<?, BossEncounterErrorCode>) result).error());
    }
}
