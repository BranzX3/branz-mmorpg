package com.branz.mmorpg.bootstrap;

import java.util.List;
import java.util.OptionalInt;

record ChroniclePlacementPlan(
        ChronicleReconcileOutcome outcome,
        OptionalInt sourceChronicleSlot,
        OptionalInt displacedValueDestination,
        List<Integer> duplicateChronicleSlots) {
    ChroniclePlacementPlan {
        duplicateChronicleSlots = List.copyOf(duplicateChronicleSlots);
    }
}
