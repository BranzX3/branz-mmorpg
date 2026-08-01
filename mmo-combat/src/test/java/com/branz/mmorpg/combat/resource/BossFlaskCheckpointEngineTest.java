package com.branz.mmorpg.combat.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BossFlaskCheckpointEngineTest {
    private final BossFlaskCheckpointEngine engine = new BossFlaskCheckpointEngine();

    @Test
    void confirmedWipeRestoresExactPreparedAllocationAndCharges() {
        UUID checkpointId = UUID.randomUUID();
        FlaskState prepared =
                new FlaskState(
                        FlaskAllocation.balanced(),
                        Map.of(FlaskDose.HEALING, 2, FlaskDose.MANA, 1, FlaskDose.STAMINA, 1));
        PreparedFlaskSnapshot snapshot = engine.capture(checkpointId, prepared);

        Result<FlaskState, FlaskCheckpointErrorCode> restored =
                engine.restore(checkpointId, snapshot, true);

        assertTrue(restored.isSuccess());
        assertEquals(
                prepared,
                ((Result.Success<FlaskState, FlaskCheckpointErrorCode>) restored).value());
    }

    @Test
    void ordinaryDeathOrAnotherCheckpointCannotCreateFreeCharges() {
        UUID checkpointId = UUID.randomUUID();
        PreparedFlaskSnapshot snapshot =
                engine.capture(checkpointId, FlaskState.full(FlaskAllocation.balanced()));

        Result<FlaskState, FlaskCheckpointErrorCode> ordinaryDeath =
                engine.restore(checkpointId, snapshot, false);
        Result<FlaskState, FlaskCheckpointErrorCode> anotherCheckpoint =
                engine.restore(UUID.randomUUID(), snapshot, true);

        assertFalse(ordinaryDeath.isSuccess());
        assertEquals(
                FlaskCheckpointErrorCode.FLASK_WIPE_NOT_CONFIRMED,
                ((Result.Failure<FlaskState, FlaskCheckpointErrorCode>) ordinaryDeath).error());
        assertFalse(anotherCheckpoint.isSuccess());
        assertEquals(
                FlaskCheckpointErrorCode.FLASK_CHECKPOINT_MISMATCH,
                ((Result.Failure<FlaskState, FlaskCheckpointErrorCode>) anotherCheckpoint).error());
    }
}
