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
    private static final int OFF_HAND_LOGICAL_SLOT = 101;

    private final BukkitItemProjectionCodec codec;

    BukkitInventoryProjectionService(BukkitItemProjectionCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    Result<ProjectionApplyReport, ProjectionApplyErrorCode> reconcile(
            Player player, PersistentCharacterSnapshot snapshot, ItemEngine itemEngine) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(itemEngine, "itemEngine");
        boolean legacyMainHand =
                snapshot.itemRecords().stream()
                        .anyMatch(
                                item ->
                                        item.location().type() == ValueLocationType.NATIVE_EQUIPPED
                                                && item.location()
                                                        .reference()
                                                        .filter("MAIN_HAND"::equals)
                                                        .isPresent());
        if (legacyMainHand) {
            return Result.failure(
                    ProjectionApplyErrorCode.PROJECTION_INVALID_DATABASE_SLOT,
                    "Legacy MAIN_HAND item must be migrated before inventory projection.");
        }

        List<ExpectedProjection> expected = snapshot.inventory();
        ItemLocationRecord offHandRecord =
                snapshot.itemRecords().stream()
                        .filter(
                                item ->
                                        item.location().type() == ValueLocationType.NATIVE_EQUIPPED
                                                && item.location()
                                                        .reference()
                                                        .filter("OFF_HAND"::equals)
                                                        .isPresent())
                        .findFirst()
                        .orElse(null);
        ExpectedProjection expectedOffHand =
                offHandRecord == null
                        ? null
                        : PersistentCharacterSnapshotMapper.itemProjection(
                                offHandRecord, OFF_HAND_LOGICAL_SLOT);

        ItemStack[] original = player.getInventory().getStorageContents();
        ItemStack[] planned = original.clone();
        List<ObservedProjection> observed = new ArrayList<>();
        Set<Integer> malformed = new HashSet<>();
        for (int slot = 0; slot < planned.length; slot++) {
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
            if (projection.slot() < 0
                    || projection.slot() >= planned.length
                    || projection.slot() == ChronicleService.HOTBAR_SLOT
                    || !authoritativeSlots.add(projection.slot())) {
                return Result.failure(
                        ProjectionApplyErrorCode.PROJECTION_INVALID_DATABASE_SLOT,
                        "Invalid or duplicate authoritative inventory slot " + projection.slot());
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
        ItemStack plannedOffHand = player.getInventory().getItemInOffHand();
        if (expectedOffHand == null) {
            if (codec.hasProjectionMarker(plannedOffHand)) {
                plannedOffHand = null;
                nativeRemoved++;
            }
        } else {
            ItemDefinition definition =
                    itemEngine.find(expectedOffHand.definitionId()).orElse(null);
            if (definition == null) {
                return Result.failure(
                        ProjectionApplyErrorCode.PROJECTION_DEFINITION_MISSING,
                        "Active content does not contain " + expectedOffHand.definitionId());
            }
            if (!matchesClass(expectedOffHand, definition)) {
                return Result.failure(
                        ProjectionApplyErrorCode.PROJECTION_CLASS_MISMATCH,
                        expectedOffHand.definitionId()
                                + " database value type does not match content");
            }
            List<ObservedProjection> observedOffHand =
                    codec.decode(plannedOffHand, OFF_HAND_LOGICAL_SLOT)
                            .map(List::of)
                            .orElseGet(List::of);
            if (codec.hasProjectionMarker(plannedOffHand) && observedOffHand.isEmpty()) {
                plannedOffHand = null;
                nativeRemoved++;
            }
            ProjectionReconciliationPlan offHandPlan =
                    InventoryProjectionReconciler.reconcile(
                            List.of(expectedOffHand), observedOffHand);
            nativeKept = offHandPlan.keepSlots().size();
            nativeRemoved += offHandPlan.removeSlots().size();
            if (!offHandPlan.removeSlots().isEmpty()) {
                plannedOffHand = null;
            }
            if (!offHandPlan.materialize().isEmpty()) {
                if (plannedOffHand != null && !plannedOffHand.getType().isAir()) {
                    int destination = findFreeSlot(planned, authoritativeSlots);
                    if (destination < 0) {
                        return Result.failure(
                                ProjectionApplyErrorCode.PROJECTION_NO_SAFE_SPACE,
                                "No free slot to preserve the current off-hand stack.");
                    }
                    planned[destination] = plannedOffHand;
                    relocated++;
                }
                plannedOffHand = codec.render(expectedOffHand, definition);
                nativeMaterialized = 1;
            }
        }

        player.getInventory().setStorageContents(planned);
        player.getInventory()
                .setItemInOffHand(
                        plannedOffHand == null
                                ? new ItemStack(org.bukkit.Material.AIR)
                                : plannedOffHand);
        return Result.success(
                new ProjectionApplyReport(
                        plan.keepSlots().size() + nativeKept,
                        removeSlots.size() + nativeRemoved,
                        plan.materialize().size() + nativeMaterialized,
                        relocated));
    }

    private static int findFreeSlot(ItemStack[] contents, Set<Integer> authoritativeSlots) {
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (slot != ChronicleService.HOTBAR_SLOT
                    && !authoritativeSlots.contains(slot)
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
