package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.items.consumable.ConsumableUseProfile;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DurableConsumableUseEngineTest {
    private static final DefinitionId TONIC = DefinitionId.of("consumable.training_body_tonic");
    private static final ConsumableUseProfile PROFILE = new ConsumableUseProfile(24, 14, 16);
    private final DurableConsumableUseEngine engine = new DurableConsumableUseEngine();

    @Test
    void preCommitInterruptDoesNotRequestLotConsumption() {
        DurableConsumableUseState started = engine.start(UUID.randomUUID(), TONIC, PROFILE, 100);

        DurableConsumableUseTransition interrupted = engine.interrupt(started, 113);

        assertEquals(DurableFlaskUsePhase.CANCELLED_BEFORE_COMMIT, interrupted.state().phase());
        assertFalse(interrupted.commitNow());
        assertFalse(interrupted.state().timeline().consumed());
    }

    @Test
    void exactCommitTickWinsAndRecoveryStartsAtAcknowledgement() {
        DurableConsumableUseState state = engine.start(UUID.randomUUID(), TONIC, PROFILE, 100);

        DurableConsumableUseTransition interrupted = engine.interrupt(state, 114);

        assertTrue(interrupted.commitNow());
        assertEquals(DurableFlaskUsePhase.COMMITTING, interrupted.state().phase());
        assertTrue(interrupted.state().interruptionRequested());
        assertEquals(
                DurableFlaskUsePhase.INTERRUPTED_AFTER_COMMIT,
                engine.commitSucceeded(interrupted.state(), 130).phase());

        state = engine.start(UUID.randomUUID(), TONIC, PROFILE, 200);
        state = engine.tick(state, 214).state();
        state = engine.commitSucceeded(state, 225);
        assertEquals(241, state.recoveryUntilTick());
        assertEquals(DurableFlaskUsePhase.COMPLETE, engine.tick(state, 241).state().phase());
    }
}
