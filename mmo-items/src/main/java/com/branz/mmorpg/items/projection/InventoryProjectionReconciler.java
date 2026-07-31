package com.branz.mmorpg.items.projection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Compares signed Bukkit projections with authoritative database expectations.
 *
 * <p>Unknown, tampered, stale, misplaced and duplicate projections are removed. A missing exact
 * projection is materialized only after removals, so a copy can never become a second database
 * value.
 */
public final class InventoryProjectionReconciler {
    private InventoryProjectionReconciler() {}

    public static ProjectionReconciliationPlan reconcile(
            List<ExpectedProjection> expected, List<ObservedProjection> observed) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(observed, "observed");

        Map<UUID, ExpectedProjection> expectedById = indexExpected(expected);
        Set<Integer> observedSlots = new HashSet<>();
        Set<UUID> kept = new HashSet<>();
        List<Integer> keepSlots = new ArrayList<>();
        List<Integer> removeSlots = new ArrayList<>();

        for (ObservedProjection candidate : observed) {
            Objects.requireNonNull(candidate, "observed projection");
            if (!observedSlots.add(candidate.slot())) {
                throw new IllegalArgumentException(
                        "multiple observed projections use slot " + candidate.slot());
            }
            ExpectedProjection authoritative = expectedById.get(candidate.valueId());
            if (authoritative != null
                    && candidate.signatureValid()
                    && exactMatch(authoritative, candidate)
                    && kept.add(candidate.valueId())) {
                keepSlots.add(candidate.slot());
            } else {
                removeSlots.add(candidate.slot());
            }
        }

        List<ExpectedProjection> materialize =
                expected.stream()
                        .filter(value -> !kept.contains(value.valueId()))
                        .sorted(Comparator.comparingInt(ExpectedProjection::slot))
                        .toList();
        keepSlots.sort(Integer::compareTo);
        removeSlots.sort(Integer::compareTo);
        return new ProjectionReconciliationPlan(keepSlots, removeSlots, materialize);
    }

    private static Map<UUID, ExpectedProjection> indexExpected(List<ExpectedProjection> expected) {
        Map<UUID, ExpectedProjection> byId = new HashMap<>();
        Set<Integer> slots = new HashSet<>();
        for (ExpectedProjection projection : expected) {
            Objects.requireNonNull(projection, "expected projection");
            if (byId.putIfAbsent(projection.valueId(), projection) != null) {
                throw new IllegalArgumentException(
                        "duplicate expected value UUID " + projection.valueId());
            }
            if (!slots.add(projection.slot())) {
                throw new IllegalArgumentException(
                        "multiple expected projections use slot " + projection.slot());
            }
        }
        return byId;
    }

    private static boolean exactMatch(ExpectedProjection expected, ObservedProjection observed) {
        return expected.slot() == observed.slot()
                && expected.definitionId().equals(observed.definitionId())
                && expected.valueType() == observed.valueType()
                && expected.quantity() == observed.quantity()
                && expected.authorityVersion() == observed.authorityVersion()
                && expected.displayRevision() == observed.displayRevision()
                && expected.contentVersion().equals(observed.contentVersion())
                && expected.testProvenance().equals(observed.testProvenance());
    }
}
