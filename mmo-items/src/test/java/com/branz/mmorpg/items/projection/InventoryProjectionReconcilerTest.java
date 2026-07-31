package com.branz.mmorpg.items.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryProjectionReconcilerTest {
    private static final DefinitionId IRON = DefinitionId.of("material.iron_ore");

    @Test
    void removesDuplicateAndKeepsOnlyTheExactAuthoritativeSlot() {
        ExpectedProjection expected = expected(UUID.randomUUID(), 4);
        ObservedProjection canonical = observed(expected, 4, true);
        ObservedProjection duplicate = observed(expected, 12, true);

        ProjectionReconciliationPlan plan =
                InventoryProjectionReconciler.reconcile(
                        List.of(expected), List.of(duplicate, canonical));

        assertEquals(List.of(4), plan.keepSlots());
        assertEquals(List.of(12), plan.removeSlots());
        assertTrue(plan.materialize().isEmpty());
    }

    @Test
    void replacesTamperedAndStaleProjectionWithoutCreatingAnotherValue() {
        ExpectedProjection expected = expected(UUID.randomUUID(), 6);
        ObservedProjection tampered = observed(expected, 6, false);
        ObservedProjection stale =
                new ObservedProjection(
                        9,
                        expected.valueId(),
                        expected.definitionId(),
                        expected.valueType(),
                        expected.quantity(),
                        expected.authorityVersion() - 1,
                        expected.displayRevision(),
                        expected.contentVersion(),
                        expected.testProvenance(),
                        true);

        ProjectionReconciliationPlan plan =
                InventoryProjectionReconciler.reconcile(
                        List.of(expected), List.of(stale, tampered));

        assertTrue(plan.keepSlots().isEmpty());
        assertEquals(List.of(6, 9), plan.removeSlots());
        assertEquals(List.of(expected), plan.materialize());
    }

    @Test
    void removesUnknownProjectionAndRecreatesMissingExpectedProjection() {
        ExpectedProjection expected = expected(UUID.randomUUID(), 2);
        ExpectedProjection unknown = expected(UUID.randomUUID(), 20);

        ProjectionReconciliationPlan plan =
                InventoryProjectionReconciler.reconcile(
                        List.of(expected), List.of(observed(unknown, 20, true)));

        assertEquals(List.of(20), plan.removeSlots());
        assertEquals(List.of(expected), plan.materialize());
    }

    @Test
    void rejectsAmbiguousDatabaseExpectations() {
        ExpectedProjection first = expected(UUID.randomUUID(), 2);
        ExpectedProjection second = expected(UUID.randomUUID(), 2);

        assertThrows(
                IllegalArgumentException.class,
                () -> InventoryProjectionReconciler.reconcile(List.of(first, second), List.of()));
    }

    @Test
    void signatureCoversSlotRevisionQuantityAndProvenance() {
        ProjectionTokenSigner signer =
                new ProjectionTokenSigner(
                        "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        ExpectedProjection expected = expected(UUID.randomUUID(), 3);
        byte[] signature = signer.sign(expected);

        assertTrue(signer.verify(observed(expected, 3, false), signature));
        assertFalse(signer.verify(observed(expected, 4, false), signature));
        signature[0] ^= 1;
        assertFalse(signer.verify(observed(expected, 3, false), signature));
    }

    private static ExpectedProjection expected(UUID valueId, int slot) {
        return new ExpectedProjection(
                valueId,
                IRON,
                ProjectionValueType.STACKABLE_LOT,
                slot,
                7,
                4,
                2,
                "content.test.1",
                Optional.of("dev:test-run"));
    }

    private static ObservedProjection observed(
            ExpectedProjection expected, int slot, boolean signatureValid) {
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
                signatureValid);
    }
}
