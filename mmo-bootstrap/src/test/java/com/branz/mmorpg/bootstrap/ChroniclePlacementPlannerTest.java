package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChroniclePlacementPlannerTest {
    @Test
    void keepsSlotNineAndRemovesOnlyDuplicateSystemItems() {
        List<ChronicleSlotState> slots = emptyInventory();
        slots.set(ChronicleService.HOTBAR_SLOT, ChronicleSlotState.CHRONICLE);
        slots.set(20, ChronicleSlotState.CHRONICLE);
        slots.set(21, ChronicleSlotState.VALUE);

        ChroniclePlacementPlan plan =
                ChroniclePlacementPlanner.plan(slots, ChronicleService.HOTBAR_SLOT);

        assertEquals(ChronicleReconcileOutcome.RESTORED, plan.outcome());
        assertEquals(List.of(20), plan.duplicateChronicleSlots());
        assertTrue(plan.displacedValueDestination().isEmpty());
    }

    @Test
    void relocatesSlotNineValueToAnEmptySlotBeforeRestoring() {
        List<ChronicleSlotState> slots = emptyInventory();
        slots.set(ChronicleService.HOTBAR_SLOT, ChronicleSlotState.VALUE);

        ChroniclePlacementPlan plan =
                ChroniclePlacementPlanner.plan(slots, ChronicleService.HOTBAR_SLOT);

        assertEquals(ChronicleReconcileOutcome.RELOCATED_AND_RESTORED, plan.outcome());
        assertEquals(0, plan.displacedValueDestination().orElseThrow());
        assertTrue(plan.sourceChronicleSlot().isEmpty());
    }

    @Test
    void swapsWithAnExistingChronicleWhenTheInventoryIsFull() {
        List<ChronicleSlotState> slots =
                new ArrayList<>(Collections.nCopies(36, ChronicleSlotState.VALUE));
        slots.set(17, ChronicleSlotState.CHRONICLE);

        ChroniclePlacementPlan plan =
                ChroniclePlacementPlanner.plan(slots, ChronicleService.HOTBAR_SLOT);

        assertEquals(ChronicleReconcileOutcome.RELOCATED_AND_RESTORED, plan.outcome());
        assertEquals(17, plan.sourceChronicleSlot().orElseThrow());
        assertEquals(17, plan.displacedValueDestination().orElseThrow());
    }

    @Test
    void refusesToOverwriteAFullInventoryWithoutAChronicle() {
        List<ChronicleSlotState> slots =
                new ArrayList<>(Collections.nCopies(36, ChronicleSlotState.VALUE));

        ChroniclePlacementPlan plan =
                ChroniclePlacementPlanner.plan(slots, ChronicleService.HOTBAR_SLOT);

        assertEquals(ChronicleReconcileOutcome.NO_SPACE, plan.outcome());
        assertTrue(plan.sourceChronicleSlot().isEmpty());
        assertTrue(plan.displacedValueDestination().isEmpty());
    }

    private static List<ChronicleSlotState> emptyInventory() {
        return new ArrayList<>(Collections.nCopies(36, ChronicleSlotState.EMPTY));
    }
}
