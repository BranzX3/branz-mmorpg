package com.branz.mmorpg.combat.crossbow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrossbowEngineTest {
    private static final DefinitionId BOLT = DefinitionId.of("ammo.test.bolt");
    private final CrossbowEngine engine = new CrossbowEngine(new CrossbowReloadProfile(12, 8));

    @Test
    void advancesOnlyAtExplicitDurableCheckpoints() {
        CrossbowRuntime unloaded = CrossbowRuntime.restore(CrossbowPersistentState.unloaded(), 100);
        CrossbowRuntime cocking = success(engine.beginOrResume(unloaded, 100));

        assertEquals(CrossbowTickOutcome.WAITING, engine.tick(cocking, 111).outcome());
        assertEquals(CrossbowTickOutcome.BOLT_BIND_REQUIRED, engine.tick(cocking, 112).outcome());

        CrossbowRuntime placed = success(engine.boltPlaced(cocking, 112, BOLT));
        assertEquals(CrossbowCheckpoint.BOLT_PLACED, placed.persistentState().checkpoint());
        CrossbowRuntime locking = success(engine.beginOrResume(placed, 112));
        assertEquals(
                CrossbowTickOutcome.LOADED_CHECKPOINT_REQUIRED,
                engine.tick(locking, 120).outcome());

        CrossbowRuntime loaded = success(engine.loaded(locking, 120));
        assertEquals(CrossbowCheckpoint.LOADED, loaded.persistentState().checkpoint());
        CrossbowFireResolution fired = success(engine.fire(loaded, 121));
        assertEquals(BOLT, fired.boundAmmoDefinitionId());
        assertEquals(CrossbowPhase.FIRED, fired.runtime().phase());
        CrossbowRuntime settled = success(engine.completeFire(fired.runtime(), 122));
        assertEquals(CrossbowCheckpoint.UNLOADED, settled.persistentState().checkpoint());
    }

    @Test
    void interruptionReturnsToTheLastCompletedCheckpoint() {
        CrossbowRuntime cocking =
                success(
                        engine.beginOrResume(
                                CrossbowRuntime.restore(CrossbowPersistentState.unloaded(), 0), 0));
        assertEquals(CrossbowPhase.UNLOADED, engine.interrupt(cocking, 4).phase());

        CrossbowRuntime placed = success(engine.boltPlaced(cocking, 12, BOLT));
        CrossbowRuntime locking = success(engine.beginOrResume(placed, 13));
        CrossbowRuntime interrupted = engine.interrupt(locking, 15);
        assertEquals(CrossbowPhase.BOLT_PLACED, interrupted.phase());
        assertEquals(Optional.of(BOLT), interrupted.boundAmmo());

        CrossbowRuntime loaded = CrossbowRuntime.restore(CrossbowPersistentState.loaded(BOLT), 20);
        assertEquals(loaded, engine.interrupt(loaded, 30));
    }

    @Test
    void rejectsEarlyOrDuplicateTransitions() {
        CrossbowRuntime cocking =
                success(
                        engine.beginOrResume(
                                CrossbowRuntime.restore(CrossbowPersistentState.unloaded(), 0), 0));
        assertFalse(engine.boltPlaced(cocking, 11, BOLT).isSuccess());
        assertFalse(engine.beginOrResume(cocking, 1).isSuccess());
        assertFalse(engine.fire(cocking, 12).isSuccess());
        assertTrue(engine.boltPlaced(cocking, 12, BOLT).isSuccess());
    }

    private static <T> T success(Result<T, CrossbowErrorCode> result) {
        return ((Result.Success<T, CrossbowErrorCode>) result).value();
    }
}
