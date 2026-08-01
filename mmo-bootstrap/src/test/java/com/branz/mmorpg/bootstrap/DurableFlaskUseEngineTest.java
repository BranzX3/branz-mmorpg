package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.combat.resource.FlaskDose;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DurableFlaskUseEngineTest {
    private final DurableFlaskUseEngine engine = new DurableFlaskUseEngine();

    @Test
    void interruptionBeforeCommitCancelsWithoutDurableMutation() {
        DurableFlaskUseState started = engine.start(UUID.randomUUID(), FlaskDose.HEALING, 100);

        DurableFlaskUseTransition interrupted = engine.interrupt(started, 117);

        assertEquals(DurableFlaskUsePhase.CANCELLED_BEFORE_COMMIT, interrupted.state().phase());
        assertFalse(interrupted.commitNow());
        assertFalse(interrupted.state().timeline().consumed());
    }

    @Test
    void exactCommitTickWinsAndWaitsForDatabaseBeforeRecovery() {
        DurableFlaskUseState started = engine.start(UUID.randomUUID(), FlaskDose.MANA, 100);

        DurableFlaskUseTransition interrupted = engine.interrupt(started, 118);

        assertEquals(DurableFlaskUsePhase.COMMITTING, interrupted.state().phase());
        assertTrue(interrupted.commitNow());
        assertTrue(interrupted.state().timeline().consumed());
        assertTrue(interrupted.state().interruptionRequested());
        assertEquals(
                DurableFlaskUsePhase.INTERRUPTED_AFTER_COMMIT,
                engine.commitSucceeded(interrupted.state(), 130).phase());
    }

    @Test
    void successfulCommitStartsRecoveryAtDatabaseAcknowledgement() {
        DurableFlaskUseState state = engine.start(UUID.randomUUID(), FlaskDose.STAMINA, 100);
        state = engine.tick(state, 118).state();

        state = engine.commitSucceeded(state, 125);

        assertEquals(DurableFlaskUsePhase.RECOVERY, state.phase());
        assertEquals(145, state.recoveryUntilTick());
        assertEquals(DurableFlaskUsePhase.RECOVERY, engine.tick(state, 144).state().phase());
        assertEquals(DurableFlaskUsePhase.COMPLETE, engine.tick(state, 145).state().phase());
    }

    @Test
    void failedDatabaseCommitEndsWithoutPretendingTheChargeWasDurable() {
        DurableFlaskUseState state = engine.start(UUID.randomUUID(), FlaskDose.HEALING, 100);
        state = engine.tick(state, 118).state();

        DurableFlaskUseState failed = engine.commitFailed(state);

        assertEquals(DurableFlaskUsePhase.COMMIT_FAILED, failed.phase());
        assertTrue(failed.phase().terminal());
    }
}
