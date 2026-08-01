package com.branz.mmorpg.social.downed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DownedEncounterEngineTest {
    private static final long START_TICK = 100;

    private final DownedEncounterEngine engine = new DownedEncounterEngine();
    private final CharacterId first = character();
    private final CharacterId second = character();

    @Test
    void firstPartyLethalDownsAndCommittedReviveConsumesTheOnlyRevive() {
        DownedEncounterRuntime runtime = start(first, second);
        DownedTransition downed =
                successful(engine.lethalDamage(runtime, first, false, operation(), START_TICK));
        runtime = downed.runtime();
        assertEquals(java.util.Set.of(first), downed.newlyDowned());
        assertEquals(
                START_TICK + DownedEncounterEngine.DOWNED_DURATION_TICKS,
                runtime.participants().get(first).downedDeadlineTick());

        runtime =
                successful(
                                engine.beginRevive(
                                        runtime,
                                        second,
                                        first,
                                        operation(),
                                        operation(),
                                        START_TICK + 10))
                        .runtime();
        DownedTransition revived =
                successful(
                        engine.advance(
                                runtime,
                                operation(),
                                START_TICK + 10 + DownedEncounterEngine.REVIVE_CHANNEL_TICKS));
        runtime = revived.runtime();
        assertEquals(
                DownedEncounterEngine.REVIVED_HEALTH_RATIO,
                revived.revivedHealthRatios().get(first));
        assertEquals(EncounterLifeState.ACTIVE, runtime.participants().get(first).lifeState());
        assertTrue(runtime.participants().get(first).reviveConsumed());
        assertTrue(runtime.participants().get(first).protectedAt(START_TICK + 91));

        DownedTransition secondLethal =
                successful(
                        engine.lethalDamage(runtime, first, false, operation(), START_TICK + 92));
        assertEquals(java.util.Set.of(first), secondLethal.newlyDead());
        assertEquals(
                EncounterLifeState.DEAD,
                secondLethal.runtime().participants().get(first).lifeState());
    }

    @Test
    void interruptedReviveDoesNotConsumeReviveAndCanRestart() {
        DownedEncounterRuntime runtime =
                successful(
                                engine.lethalDamage(
                                        start(first, second),
                                        first,
                                        false,
                                        operation(),
                                        START_TICK))
                        .runtime();
        runtime =
                successful(
                                engine.beginRevive(
                                        runtime,
                                        second,
                                        first,
                                        operation(),
                                        operation(),
                                        START_TICK + 1))
                        .runtime();
        DownedTransition interrupted =
                successful(engine.interruptRevive(runtime, first, operation()));

        assertTrue(interrupted.runtime().reviveChannelsByTarget().isEmpty());
        assertFalse(interrupted.runtime().participants().get(first).reviveConsumed());
        assertTrue(
                engine.beginRevive(
                                interrupted.runtime(),
                                second,
                                first,
                                operation(),
                                operation(),
                                START_TICK + 2)
                        .isSuccess());
    }

    @Test
    void soloAndExecuteLethalDamageBypassDownedState() {
        DownedTransition solo =
                successful(
                        engine.lethalDamage(start(first), first, false, operation(), START_TICK));
        assertEquals(EncounterLifeState.DEAD, solo.runtime().participants().get(first).lifeState());

        DownedTransition executed =
                successful(
                        engine.lethalDamage(
                                start(first, second), first, true, operation(), START_TICK));
        assertEquals(
                EncounterLifeState.DEAD, executed.runtime().participants().get(first).lifeState());
    }

    @Test
    void downedExpiryWinsOverAnUnfinishedReviveAtTheSameTick() {
        DownedEncounterRuntime runtime =
                successful(
                                engine.lethalDamage(
                                        start(first, second),
                                        first,
                                        false,
                                        operation(),
                                        START_TICK))
                        .runtime();
        runtime =
                successful(
                                engine.beginRevive(
                                        runtime,
                                        second,
                                        first,
                                        operation(),
                                        operation(),
                                        START_TICK
                                                + DownedEncounterEngine.DOWNED_DURATION_TICKS
                                                - 40))
                        .runtime();
        DownedTransition expired =
                successful(
                        engine.advance(
                                runtime,
                                operation(),
                                START_TICK + DownedEncounterEngine.DOWNED_DURATION_TICKS));

        assertEquals(java.util.Set.of(first), expired.newlyDead());
        assertTrue(expired.revivedHealthRatios().isEmpty());
        assertTrue(expired.runtime().reviveChannelsByTarget().isEmpty());
    }

    @Test
    void hostileActionEndsReviveProtectionAndOperationReplayEmitsNothing() {
        DownedEncounterRuntime runtime = revivedRuntime();
        UUID hostileOperation = operation();
        DownedTransition ended =
                successful(
                        engine.hostileAction(runtime, first, hostileOperation, START_TICK + 100));
        assertFalse(ended.runtime().participants().get(first).protectedAt(START_TICK + 100));

        DownedTransition replay =
                successful(
                        engine.hostileAction(
                                ended.runtime(), first, hostileOperation, START_TICK + 101));
        assertFalse(replay.changed());
        assertTrue(replay.revivedHealthRatios().isEmpty());
    }

    @Test
    void reusedOperationIdForAnotherCommandFailsClosed() {
        DownedEncounterRuntime runtime = start(first, second);
        UUID operationId = operation();
        runtime =
                successful(engine.lethalDamage(runtime, first, false, operationId, START_TICK))
                        .runtime();

        Result<DownedTransition, DownedErrorCode> reused =
                engine.beginRevive(
                        runtime, second, first, operation(), operationId, START_TICK + 1);
        assertFalse(reused.isSuccess());
        assertEquals(
                DownedErrorCode.OPERATION_ID_REUSED,
                ((Result.Failure<DownedTransition, DownedErrorCode>) reused).error());
    }

    private DownedEncounterRuntime revivedRuntime() {
        DownedEncounterRuntime runtime =
                successful(
                                engine.lethalDamage(
                                        start(first, second),
                                        first,
                                        false,
                                        operation(),
                                        START_TICK))
                        .runtime();
        runtime =
                successful(
                                engine.beginRevive(
                                        runtime,
                                        second,
                                        first,
                                        operation(),
                                        operation(),
                                        START_TICK + 1))
                        .runtime();
        return successful(
                        engine.advance(
                                runtime,
                                operation(),
                                START_TICK + 1 + DownedEncounterEngine.REVIVE_CHANNEL_TICKS))
                .runtime();
    }

    private DownedEncounterRuntime start(CharacterId... participants) {
        return successful(engine.start(new EncounterId(operation()), List.of(participants)));
    }

    private static CharacterId character() {
        return new CharacterId(operation());
    }

    private static UUID operation() {
        return UUID.randomUUID();
    }

    private static <T> T successful(Result<T, DownedErrorCode> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<T, DownedErrorCode>) result).value();
    }
}
