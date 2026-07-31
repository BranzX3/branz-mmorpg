package com.branz.mmorpg.bootstrap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

final class ChroniclePlacementPlanner {
    private ChroniclePlacementPlanner() {}

    static ChroniclePlacementPlan plan(List<ChronicleSlotState> slots, int targetSlot) {
        Objects.requireNonNull(slots, "slots");
        if (targetSlot < 0 || targetSlot >= slots.size()) {
            throw new IllegalArgumentException("targetSlot is outside inventory");
        }
        if (slots.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("slot states must not contain null");
        }

        ChronicleSlotState target = slots.get(targetSlot);
        List<Integer> chronicleSlots = new ArrayList<>();
        int emptySlot = -1;
        for (int slot = 0; slot < slots.size(); slot++) {
            ChronicleSlotState state = slots.get(slot);
            if (state == ChronicleSlotState.CHRONICLE) {
                chronicleSlots.add(slot);
            } else if (slot != targetSlot && emptySlot < 0 && state == ChronicleSlotState.EMPTY) {
                emptySlot = slot;
            }
        }

        if (target == ChronicleSlotState.CHRONICLE) {
            List<Integer> duplicates =
                    chronicleSlots.stream().filter(slot -> slot != targetSlot).toList();
            return new ChroniclePlacementPlan(
                    duplicates.isEmpty()
                            ? ChronicleReconcileOutcome.UNCHANGED
                            : ChronicleReconcileOutcome.RESTORED,
                    OptionalInt.of(targetSlot),
                    OptionalInt.empty(),
                    duplicates);
        }

        int source =
                chronicleSlots.stream().filter(slot -> slot != targetSlot).findFirst().orElse(-1);
        int destination = -1;
        if (target == ChronicleSlotState.VALUE) {
            destination = emptySlot >= 0 ? emptySlot : source;
            if (destination < 0) {
                return new ChroniclePlacementPlan(
                        ChronicleReconcileOutcome.NO_SPACE,
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        List.of());
            }
        }
        List<Integer> duplicates = chronicleSlots.stream().filter(slot -> slot != source).toList();
        return new ChroniclePlacementPlan(
                target == ChronicleSlotState.VALUE
                        ? ChronicleReconcileOutcome.RELOCATED_AND_RESTORED
                        : ChronicleReconcileOutcome.RESTORED,
                source < 0 ? OptionalInt.empty() : OptionalInt.of(source),
                destination < 0 ? OptionalInt.empty() : OptionalInt.of(destination),
                duplicates);
    }
}
