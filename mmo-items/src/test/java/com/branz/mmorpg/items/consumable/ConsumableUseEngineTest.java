package com.branz.mmorpg.items.consumable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsumableUseEngineTest {
    private final ConsumableUseEngine engine = new ConsumableUseEngine();

    @Test
    void interruptionBeforeCommitCancelsWithoutConsumption() {
        ConsumableUseState started = state(100);

        ConsumableUseTransition cancelled = engine.advance(started, 117, true);

        assertEquals(ConsumableUsePhase.CANCELLED_BEFORE_COMMIT, cancelled.state().phase());
        assertFalse(cancelled.state().consumed());
        assertFalse(cancelled.commitNow());
    }

    @Test
    void commitTickWinsThenInterruptionCannotRefundTheConsumedValue() {
        ConsumableUseState started = state(100);

        ConsumableUseTransition interrupted = engine.advance(started, 118, true);

        assertEquals(ConsumableUsePhase.INTERRUPTED_AFTER_COMMIT, interrupted.state().phase());
        assertTrue(interrupted.state().consumed());
        assertTrue(interrupted.commitNow());
        assertFalse(engine.advance(interrupted.state(), 140, false).commitNow());
    }

    @Test
    void emitsOneCommitAcrossWindupRecoveryAndCompletion() {
        ConsumableUseState started = state(100);
        ConsumableUseTransition committed = engine.advance(started, 118, false);
        ConsumableUseTransition recovery = engine.advance(committed.state(), 128, false);
        ConsumableUseTransition complete = engine.advance(recovery.state(), 148, false);

        assertTrue(committed.commitNow());
        assertEquals(ConsumableUsePhase.COMMITTED, committed.state().phase());
        assertFalse(recovery.commitNow());
        assertEquals(ConsumableUsePhase.RECOVERY, recovery.state().phase());
        assertFalse(complete.commitNow());
        assertEquals(ConsumableUsePhase.COMPLETE, complete.state().phase());
    }

    private static ConsumableUseState state(long tick) {
        return ConsumableUseState.start(
                UUID.randomUUID(),
                DefinitionId.of("consumable.expedition_flask"),
                ConsumableUseProfile.expeditionFlask(),
                tick);
    }
}
