package com.branz.mmorpg.items.projection;

import com.branz.mmorpg.api.result.Result;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Interprets a post-inventory-action projection layout as unchanged, transient cursor ownership,
 * one durable move, or one exact two-unique-item swap.
 *
 * <p>Unique items may move to an empty slot or exact-swap with another unique item. Stackable lots
 * may move only as a whole stack to a database-empty slot. Split, merge and mixed swap remain
 * fail-closed until their persistence contracts are explicitly owned.
 *
 * <p>Signed placement is never database truth. Signatures prove value identity/version while the
 * expected layout remains authoritative until the returned intent commits transactionally.
 */
public final class InventoryProjectionMovePlanner {
    private InventoryProjectionMovePlanner() {}

    public static Result<ProjectionMovePlan, ProjectionMoveErrorCode> plan(
            List<ExpectedProjection> expected,
            List<ObservedProjection> storageObserved,
            Optional<ObservedProjection> cursorObserved,
            int storageSize,
            int protectedSlot) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(storageObserved, "storageObserved");
        Objects.requireNonNull(cursorObserved, "cursorObserved");
        if (storageSize < 1 || protectedSlot < 0 || protectedSlot >= storageSize) {
            throw new IllegalArgumentException("invalid physical inventory bounds");
        }

        Map<UUID, ExpectedProjection> expectedById = new HashMap<>();
        Map<Integer, ExpectedProjection> expectedBySlot = new HashMap<>();
        for (ExpectedProjection projection : expected) {
            Objects.requireNonNull(projection, "expected projection");
            if (projection.slot() < 0
                    || projection.slot() >= storageSize
                    || projection.slot() == protectedSlot) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_PROTECTED_SLOT,
                        "Authoritative projection occupies a non-gameplay/protected slot.");
            }
            if (expectedById.putIfAbsent(projection.valueId(), projection) != null
                    || expectedBySlot.putIfAbsent(projection.slot(), projection) != null) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_INVALID,
                        "Authoritative projection layout contains duplicate UUID or slot.");
            }
        }

        Map<UUID, ObservedProjection> storageById = new HashMap<>();
        Set<Integer> observedSlots = new HashSet<>();
        for (ObservedProjection projection : storageObserved) {
            Objects.requireNonNull(projection, "observed projection");
            if (projection.slot() < 0
                    || projection.slot() >= storageSize
                    || projection.slot() == protectedSlot) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_PROTECTED_SLOT,
                        "MMO projection moved into a non-gameplay/protected slot.");
            }
            if (!projection.signatureValid()) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_INVALID,
                        "Observed MMO projection has an invalid signature.");
            }
            if (!observedSlots.add(projection.slot())
                    || storageById.putIfAbsent(projection.valueId(), projection) != null) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_DUPLICATE,
                        "Observed projection layout duplicates UUID or slot.");
            }
            ExpectedProjection authoritative = expectedById.get(projection.valueId());
            if (authoritative == null) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_UNKNOWN,
                        "Observed projection UUID is not owned by this authoritative inventory.");
            }
            if (!sameSignedIdentity(authoritative, projection)) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_INVALID,
                        "Observed projection identity/version is stale or inconsistent.");
            }
        }

        ObservedProjection cursor = cursorObserved.orElse(null);
        if (cursor != null) {
            if (!cursor.signatureValid()) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_INVALID,
                        "Cursor MMO projection has an invalid signature.");
            }
            ExpectedProjection authoritative = expectedById.get(cursor.valueId());
            if (authoritative == null) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_UNKNOWN,
                        "Cursor projection UUID is not owned by this authoritative inventory.");
            }
            if (storageById.containsKey(cursor.valueId())) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_DUPLICATE,
                        "Cursor projection duplicates an MMO value still present in storage.");
            }
            if (!sameSignedIdentity(authoritative, cursor)) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_INVALID,
                        "Cursor projection identity/version is stale or inconsistent. Partial stack movement is not supported.");
            }
        }

        List<PlacementChange> changed = new ArrayList<>();
        for (ExpectedProjection authoritative : expected) {
            ObservedProjection observed = storageById.get(authoritative.valueId());
            boolean onCursor = cursor != null && cursor.valueId().equals(authoritative.valueId());
            if (observed == null && !onCursor) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_MISSING,
                        "Authoritative MMO value disappeared from storage/cursor observation.");
            }
            if (!onCursor && observed.slot() != authoritative.slot()) {
                changed.add(new PlacementChange(authoritative, observed.slot()));
            }
        }

        if (cursor != null) {
            if (changed.size() > 1) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_PERMUTATION_UNSUPPORTED,
                        "Cursor state contains more than one additional MMO value movement.");
            }
            return Result.success(ProjectionMovePlan.transientCursor());
        }
        if (changed.isEmpty()) {
            return Result.success(ProjectionMovePlan.unchanged());
        }
        if (changed.size() == 1) {
            PlacementChange change = changed.getFirst();
            ExpectedProjection priorDestination = expectedBySlot.get(change.destinationSlot());
            if (priorDestination != null && !priorDestination.valueId().equals(change.valueId())) {
                return Result.failure(
                        change.valueType() == ProjectionValueType.STACKABLE_LOT
                                        || priorDestination.valueType()
                                                == ProjectionValueType.STACKABLE_LOT
                                ? ProjectionMoveErrorCode.PROJECTION_MOVE_STACKABLE_UNSUPPORTED
                                : ProjectionMoveErrorCode.PROJECTION_MOVE_PERMUTATION_UNSUPPORTED,
                        "Destination was occupied by another authoritative value. Lot merge/mixed swap is unsupported.");
            }
            return Result.success(
                    ProjectionMovePlan.ready(
                            new ProjectionMoveIntent(
                                    change.valueId(),
                                    change.valueType(),
                                    change.sourceSlot(),
                                    change.destinationSlot(),
                                    false)));
        }
        if (changed.size() == 2) {
            changed.sort(Comparator.comparing(change -> change.valueId().toString()));
            PlacementChange first = changed.get(0);
            PlacementChange second = changed.get(1);
            if (first.valueType() != ProjectionValueType.UNIQUE_ITEM
                    || second.valueType() != ProjectionValueType.UNIQUE_ITEM) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_STACKABLE_UNSUPPORTED,
                        "Stackable lot swap, merge or mixed permutation is unsupported.");
            }
            boolean exactSwap =
                    first.destinationSlot() == second.sourceSlot()
                            && second.destinationSlot() == first.sourceSlot();
            if (!exactSwap) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_PERMUTATION_UNSUPPORTED,
                        "Only one value move or an exact two-unique-item swap is supported.");
            }
            return Result.success(
                    ProjectionMovePlan.ready(
                            new ProjectionMoveIntent(
                                    first.valueId(),
                                    ProjectionValueType.UNIQUE_ITEM,
                                    first.sourceSlot(),
                                    first.destinationSlot(),
                                    true)));
        }
        return Result.failure(
                ProjectionMoveErrorCode.PROJECTION_MOVE_PERMUTATION_UNSUPPORTED,
                "More than two value placements changed in one observation.");
    }

    private static boolean sameSignedIdentity(
            ExpectedProjection expected, ObservedProjection observed) {
        return expected.valueId().equals(observed.valueId())
                && expected.definitionId().equals(observed.definitionId())
                && expected.valueType() == observed.valueType()
                && expected.quantity() == observed.quantity()
                && expected.authorityVersion() == observed.authorityVersion()
                && expected.displayRevision() == observed.displayRevision()
                && expected.contentVersion().equals(observed.contentVersion())
                && expected.testProvenance().equals(observed.testProvenance());
    }

    private record PlacementChange(ExpectedProjection authoritative, int destinationSlot) {
        private UUID valueId() {
            return authoritative.valueId();
        }

        private ProjectionValueType valueType() {
            return authoritative.valueType();
        }

        private int sourceSlot() {
            return authoritative.slot();
        }
    }
}
