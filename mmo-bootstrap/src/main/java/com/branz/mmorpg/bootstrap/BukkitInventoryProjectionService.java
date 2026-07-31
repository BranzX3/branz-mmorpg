package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.items.projection.ExpectedProjection;
import com.branz.mmorpg.items.projection.InventoryProjectionReconciler;
import com.branz.mmorpg.items.projection.ObservedProjection;
import com.branz.mmorpg.items.projection.ProjectionReconciliationPlan;
import com.branz.mmorpg.items.projection.ProjectionValueType;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocationType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Applies a complete projection repair to a cloned inventory before one Bukkit mutation. */
final class BukkitInventoryProjectionService {
    private static final int MAIN_HAND_LOGICAL_SLOT = 100;
    private static final int MAIN_HAND_PHYSICAL_SLOT = 0;

    private final BukkitItemProjectionCodec codec;

    BukkitInventoryProjectionService(BukkitItemProjectionCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    Result<ProjectionApplyReport, ProjectionApplyErrorCode> reconcile(
            Player player, PersistentCharacterSnapshot snapshot, ItemEngine itemEngine) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(itemEngine, "itemEngine");
        List<ExpectedProjection> expected = snapshot.inventory();
        ItemLocationRecord mainHandRecord =
                snapshot.itemRecords().stream()
                        .filter(
                                item ->
                                        item.location().type() == ValueLocationType.NATIVE_EQUIPPED
                                                && item.location()
                                                        .reference()
                                                        .filter("MAIN_HAND"::equals)
                                                        .isPresent())
                        .findFirst()
                        .orElse(null);
        ExpectedProjection expectedMainHand =
                mainHandRecord == null
                        ? null
                        : PersistentCharacterSnapshotMapper.itemProjection(
                                mainHandRecord, MAIN_HAND_LOGICAL_SLOT);

        ItemStack[] original = player.getInventory().getStorageContents();
        ItemStack[] planned = original.clone();
        List<ObservedProjection> observed = new ArrayList<>();
        Set<Integer> malformed = new HashSet<>();
        for (int slot = 0; slot < planned.length; slot++) {
            if (slot == MAIN_HAND_PHYSICAL_SLOT && expectedMainHand != null) {
                continue;
            }
            ItemStack item = planned[slot];
            if (!codec.hasProjectionMarker(item)) {
                continue;
            }
            int observedSlot = slot;
            codec.decode(item, slot)
                    .ifPresentOrElse(observed::add, () -> malformed.add(observedSlot));
        }

        ProjectionReconciliationPlan plan;
        try {
            plan = InventoryProjectionReconciler.reconcile(expected, observed);
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    ProjectionApplyErrorCode.PROJECTION_INVALID_DATABASE_SLOT,
                    exception.getMessage());
        }
        Set<Integer> removeSlots = new HashSet<>(plan.removeSlots());
        removeSlots.addAll(malformed);
        for (int slot : removeSlots) {
            planned[slot] = null;
        }

        Set<Integer> authoritativeSlots = new HashSet<>();
        for (ExpectedProjection projection : expected) {
            if (projection.slot() >= planned.length
                    || projection.slot() == ChronicleService.HOTBAR_SLOT
                    || !authoritativeSlots.add(projection.slot())) {
                return Result.failure(
                        ProjectionApplyErrorCode.PROJECTION_INVALID_DATABASE_SLOT,
                        "Invalid or duplicate authoritative inventory slot " + projection.slot());
            }
            if (projection.slot() == MAIN_HAND_PHYSICAL_SLOT && expectedMainHand != null) {
                return Result.failure(
                        ProjectionApplyErrorCode.PROJECTION_INVALID_DATABASE_SLOT,
                        "Inventory slot 0 conflicts with native main-hand projection.");
            }
            ItemDefinition definition = itemEngine.find(projection.definitionId()).orElse(null);
            if (definition == null) {
                return Result.failure(
                        ProjectionApplyErrorCode.PROJECTION_DEFINITION_MISSING,
                        "Active content does not contain " + projection.definitionId());
            }
            if (!matchesClass(projection, definition)) {
                return Result.failure(
                        ProjectionApplyErrorCode.PROJECTION_CLASS_MISMATCH,
                        projection.definitionId() + " database value type does not match content");
            }
        }

        int relocated = 0;
        for (ExpectedProjection projection : plan.materialize()) {
            int target = projection.slot();
            ItemStack displaced = planned[target];
            if (displaced != null && !displaced.getType().isAir()) {
                int destination = findFreeSlot(planned, authoritativeSlots);
                if (destination < 0) {
                    return Result.failure(
                            ProjectionApplyErrorCode.PROJECTION_NO_SAFE_SPACE,
                            "No free slot to preserve the value occupying slot " + target);
                }
                planned[destination] = displaced;
                relocated++;
            }
            ItemDefinition definition = itemEngine.find(projection.definitionId()).orElseThrow();
            planned[target] = codec.render(projection, definition);
        }

        int nativeKept = 0;
        int nativeRemoved = 0;
        int nativeMaterialized = 0;
        if (expectedMainHand != null) {
            ItemDefinition definition =
                    itemEngine.find(expectedMainHand.definitionId()).orElse(null);
            if (definition == null) {
                return Result.failure(
                        ProjectionApplyErrorCode.PROJECTION_DEFINITION_MISSING,
                        "Active content does not contain " + expectedMainHand.definitionId());
            }
            if (!matchesClass(expectedMainHand, definition)) {
                return Result.failure(
                        ProjectionApplyErrorCode.PROJECTION_CLASS_MISMATCH,
                        expectedMainHand.definitionId()
                                + " database value type does not match content");
            }
            List<ObservedProjection> observedMain =
                    codec.decode(original[MAIN_HAND_PHYSICAL_SLOT], MAIN_HAND_LOGICAL_SLOT)
                            .map(List::of)
                            .orElseGet(List::of);
            if (codec.hasProjectionMarker(original[MAIN_HAND_PHYSICAL_SLOT])
                    && observedMain.isEmpty()) {
                nativeRemoved++;
                planned[MAIN_HAND_PHYSICAL_SLOT] = null;
            }
            ProjectionReconciliationPlan nativePlan =
                    InventoryProjectionReconciler.reconcile(
                            List.of(expectedMainHand), observedMain);
            nativeKept = nativePlan.keepSlots().size();
            nativeRemoved += nativePlan.removeSlots().size();
            if (!nativePlan.removeSlots().isEmpty()) {
                planned[MAIN_HAND_PHYSICAL_SLOT] = null;
            }
            if (!nativePlan.materialize().isEmpty()) {
                ItemStack displaced = planned[MAIN_HAND_PHYSICAL_SLOT];
                if (displaced != null && !displaced.getType().isAir()) {
                    int destination =
                            findFreeSlot(
                                    planned, authoritativeSlots, Set.of(MAIN_HAND_PHYSICAL_SLOT));
                    if (destination < 0) {
                        return Result.failure(
                                ProjectionApplyErrorCode.PROJECTION_NO_SAFE_SPACE,
                                "No free slot to preserve the current main-hand stack.");
                    }
                    planned[destination] = displaced;
                    relocated++;
                }
                planned[MAIN_HAND_PHYSICAL_SLOT] = codec.render(expectedMainHand, definition);
                nativeMaterialized++;
            }
        }

        player.getInventory().setStorageContents(planned);
        if (expectedMainHand != null) {
            player.getInventory().setHeldItemSlot(MAIN_HAND_PHYSICAL_SLOT);
        }
        return Result.success(
                new ProjectionApplyReport(
                        plan.keepSlots().size() + nativeKept,
                        removeSlots.size() + nativeRemoved,
                        plan.materialize().size() + nativeMaterialized,
                        relocated));
    }

    private static int findFreeSlot(ItemStack[] contents, Set<Integer> authoritativeSlots) {
        return findFreeSlot(contents, authoritativeSlots, Set.of());
    }

    private static int findFreeSlot(
            ItemStack[] contents,
            Set<Integer> authoritativeSlots,
            Set<Integer> additionallyReserved) {
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (slot != ChronicleService.HOTBAR_SLOT
                    && !authoritativeSlots.contains(slot)
                    && !additionallyReserved.contains(slot)
                    && (item == null || item.getType().isAir())) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean matchesClass(ExpectedProjection projection, ItemDefinition definition) {
        return projection.valueType() == ProjectionValueType.UNIQUE_ITEM
                ? definition.itemClass() == ItemClass.UNIQUE_DURABLE
                : definition.itemClass() == ItemClass.STACKABLE_LOT;
    }
}
