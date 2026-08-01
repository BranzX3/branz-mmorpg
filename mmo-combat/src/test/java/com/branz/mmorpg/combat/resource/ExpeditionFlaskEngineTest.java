package com.branz.mmorpg.combat.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExpeditionFlaskEngineTest {
    private final ExpeditionFlaskEngine engine = new ExpeditionFlaskEngine();

    @Test
    void consumesExactlyOneAllocatedDoseAndReturnsItsBoundedRestoration() {
        FlaskState full = FlaskState.full(FlaskAllocation.balanced());

        Result<FlaskConsumption, FlaskErrorCode> result = engine.consume(full, FlaskDose.STAMINA);

        assertTrue(result.isSuccess());
        FlaskConsumption consumed =
                ((Result.Success<FlaskConsumption, FlaskErrorCode>) result).value();
        assertEquals(0, consumed.state().charge(FlaskDose.STAMINA));
        assertEquals(60, consumed.restoration().stamina());
        assertTrue(consumed.restoration().clearsExhausted());
        assertEquals(4, consumed.state().totalCharges());
        assertFalse(engine.consume(consumed.state(), FlaskDose.STAMINA).isSuccess());
    }

    @Test
    void restCarriesCompatibleChargesAndConsumesOnlyMissingStockInStableOrder() {
        FlaskAllocation currentAllocation = FlaskAllocation.balanced();
        FlaskState current =
                new FlaskState(
                        currentAllocation,
                        Map.of(FlaskDose.HEALING, 1, FlaskDose.MANA, 1, FlaskDose.STAMINA, 0));
        FlaskAllocation desired =
                new FlaskAllocation(
                        5, Map.of(FlaskDose.HEALING, 1, FlaskDose.MANA, 2, FlaskDose.STAMINA, 2));

        FlaskPreparation prepared = success(engine.prepare(current, desired, 2, false));

        assertEquals(2, prepared.infusionStockConsumed());
        assertEquals(0, prepared.mercyChargesGranted());
        assertEquals(1, prepared.state().charge(FlaskDose.HEALING));
        assertEquals(2, prepared.state().charge(FlaskDose.MANA));
        assertEquals(1, prepared.state().charge(FlaskDose.STAMINA));
    }

    @Test
    void mercyRaisesAnEmptyPreparationToTwoChargesWithoutFabricatingStock() {
        FlaskAllocation allocation = FlaskAllocation.balanced();

        FlaskPreparation prepared =
                success(engine.prepare(FlaskState.empty(allocation), allocation, 0, true));

        assertEquals(0, prepared.infusionStockConsumed());
        assertEquals(2, prepared.mercyChargesGranted());
        assertEquals(2, prepared.state().totalCharges());
    }

    private static FlaskPreparation success(Result<FlaskPreparation, FlaskErrorCode> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<FlaskPreparation, FlaskErrorCode>) result).value();
    }
}
