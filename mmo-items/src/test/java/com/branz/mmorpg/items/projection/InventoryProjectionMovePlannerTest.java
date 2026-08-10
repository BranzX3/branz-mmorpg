package com.branz.mmorpg.items.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryProjectionMovePlannerTest {
    private static final int STORAGE_SIZE = 36;
    private static final int CHRONICLE_SLOT = 8;
    private static final DefinitionId SWORD = DefinitionId.of("weapon.test.sword");
    private static final DefinitionId CHARM = DefinitionId.of("item.test.charm");
    private static final DefinitionId ORE = DefinitionId.of("material.test.ore");

    @Test
    void plansOneUniqueItemMoveToEmptySlot() {
        ExpectedProjection sword = unique(SWORD, 12, 3);

        ProjectionMovePlan plan =
                success(List.of(sword), List.of(observed(sword, 3)), Optional.empty());

        assertEquals(ProjectionMoveDisposition.READY_TO_COMMIT, plan.disposition());
        ProjectionMoveIntent intent = plan.intent().orElseThrow();
        assertEquals(sword.valueId(), intent.valueId());
        assertEquals(12, intent.sourceSlot());
        assertEquals(3, intent.destinationSlot());
        assertFalse(intent.swap());
    }

    @Test
    void plansExactTwoUniqueItemSwap() {
        ExpectedProjection sword = unique(SWORD, 12, 3);
        ExpectedProjection charm = unique(CHARM, 5, 8);

        ProjectionMovePlan plan =
                success(
                        List.of(sword, charm),
                        List.of(observed(sword, 5), observed(charm, 12)),
                        Optional.empty());

        assertEquals(ProjectionMoveDisposition.READY_TO_COMMIT, plan.disposition());
        assertTrue(plan.intent().orElseThrow().swap());
    }

    @Test
    void pickupOntoCursorIsTransientAndDoesNotCreateCommitIntent() {
        ExpectedProjection sword = unique(SWORD, 12, 3);

        ProjectionMovePlan plan =
                success(List.of(sword), List.of(), Optional.of(observed(sword, 1000)));

        assertEquals(ProjectionMoveDisposition.TRANSIENT_CURSOR, plan.disposition());
        assertTrue(plan.intent().isEmpty());
    }

    @Test
    void occupiedSlotSwapIntermediateRemainsTransientUntilCursorIsPlaced() {
        ExpectedProjection sword = unique(SWORD, 12, 3);
        ExpectedProjection charm = unique(CHARM, 5, 8);

        ProjectionMovePlan plan =
                success(
                        List.of(sword, charm),
                        List.of(observed(sword, 5)),
                        Optional.of(observed(charm, 1000)));

        assertEquals(ProjectionMoveDisposition.TRANSIENT_CURSOR, plan.disposition());
        assertTrue(plan.intent().isEmpty());
    }

    @Test
    void unchangedLayoutProducesNoCommitIntent() {
        ExpectedProjection sword = unique(SWORD, 2, 4);

        ProjectionMovePlan plan =
                success(List.of(sword), List.of(observed(sword, sword.slot())), Optional.empty());

        assertEquals(ProjectionMoveDisposition.UNCHANGED, plan.disposition());
        assertTrue(plan.intent().isEmpty());
    }

    @Test
    void staleSignedIdentityIsRejectedEvenWhenSlotMovementWouldBeValid() {
        ExpectedProjection sword = unique(SWORD, 12, 7);
        ObservedProjection stale =
                new ObservedProjection(
                        3,
                        sword.valueId(),
                        sword.definitionId(),
                        sword.valueType(),
                        sword.quantity(),
                        sword.authorityVersion() - 1,
                        sword.displayRevision(),
                        sword.contentVersion(),
                        sword.testProvenance(),
                        true);

        assertFailure(
                List.of(sword),
                List.of(stale),
                Optional.empty(),
                ProjectionMoveErrorCode.PROJECTION_MOVE_INVALID);
    }

    @Test
    void signedUnknownUuidCannotBecomeMoveIntent() {
        ExpectedProjection sword = unique(SWORD, 12, 7);
        ExpectedProjection foreign = unique(CHARM, 20, 4);

        assertFailure(
                List.of(sword),
                List.of(observed(foreign, 3)),
                Optional.empty(),
                ProjectionMoveErrorCode.PROJECTION_MOVE_UNKNOWN);
    }

    @Test
    void duplicateUuidAcrossStorageAndCursorIsRejected() {
        ExpectedProjection sword = unique(SWORD, 12, 3);

        assertFailure(
                List.of(sword),
                List.of(observed(sword, 3)),
                Optional.of(observed(sword, 1000)),
                ProjectionMoveErrorCode.PROJECTION_MOVE_DUPLICATE);
    }

    @Test
    void stackableLotMovementIsRejectedFromUniqueItemSlice() {
        ExpectedProjection ore = lot(ORE, 14, 20, 4);

        assertFailure(
                List.of(ore),
                List.of(observed(ore, 2)),
                Optional.empty(),
                ProjectionMoveErrorCode.PROJECTION_MOVE_STACKABLE_UNSUPPORTED);
    }

    @Test
    void protectedChronicleSlotIsRejected() {
        ExpectedProjection sword = unique(SWORD, 12, 3);

        assertFailure(
                List.of(sword),
                List.of(observed(sword, CHRONICLE_SLOT)),
                Optional.empty(),
                ProjectionMoveErrorCode.PROJECTION_MOVE_PROTECTED_SLOT);
    }

    private static ProjectionMovePlan success(
            List<ExpectedProjection> expected,
            List<ObservedProjection> storage,
            Optional<ObservedProjection> cursor) {
        Result<ProjectionMovePlan, ProjectionMoveErrorCode> result =
                InventoryProjectionMovePlanner.plan(
                        expected, storage, cursor, STORAGE_SIZE, CHRONICLE_SLOT);
        if (!result.isSuccess()) {
            Result.Failure<ProjectionMovePlan, ProjectionMoveErrorCode> failure =
                    (Result.Failure<ProjectionMovePlan, ProjectionMoveErrorCode>) result;
            throw new AssertionError(failure.error().code() + ": " + failure.detail());
        }
        return ((Result.Success<ProjectionMovePlan, ProjectionMoveErrorCode>) result).value();
    }

    private static void assertFailure(
            List<ExpectedProjection> expected,
            List<ObservedProjection> storage,
            Optional<ObservedProjection> cursor,
            ProjectionMoveErrorCode error) {
        Result<ProjectionMovePlan, ProjectionMoveErrorCode> result =
                InventoryProjectionMovePlanner.plan(
                        expected, storage, cursor, STORAGE_SIZE, CHRONICLE_SLOT);
        assertTrue(result instanceof Result.Failure<?, ?>);
        assertEquals(
                error,
                ((Result.Failure<ProjectionMovePlan, ProjectionMoveErrorCode>) result).error());
    }

    private static ExpectedProjection unique(DefinitionId definition, int slot, long version) {
        return new ExpectedProjection(
                UUID.randomUUID(),
                definition,
                ProjectionValueType.UNIQUE_ITEM,
                slot,
                1,
                version,
                2,
                "content.test.1",
                Optional.empty());
    }

    private static ExpectedProjection lot(
            DefinitionId definition, int slot, int quantity, long version) {
        return new ExpectedProjection(
                UUID.randomUUID(),
                definition,
                ProjectionValueType.STACKABLE_LOT,
                slot,
                quantity,
                version,
                2,
                "content.test.1",
                Optional.empty());
    }

    private static ObservedProjection observed(ExpectedProjection expected, int slot) {
        return new ObservedProjection(
                slot,
                expected.valueId(),
                expected.definitionId(),
                expected.valueType(),
                expected.quantity(),
                expected.authorityVersion(),
                expected.displayRevision(),
                expected.contentVersion(),
                expected.testProvenance(),
                true);
    }
}
