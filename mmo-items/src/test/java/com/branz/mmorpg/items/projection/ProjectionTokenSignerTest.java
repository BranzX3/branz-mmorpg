package com.branz.mmorpg.items.projection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectionTokenSignerTest {
    private static final DefinitionId SWORD = DefinitionId.of("weapon.test.sword");

    @Test
    void validProjectionSignatureSurvivesPhysicalSlotMovement() {
        ProjectionTokenSigner signer = signer();
        ExpectedProjection expected = expected(12, 4);
        byte[] signature = signer.sign(expected);

        ObservedProjection moved = observed(expected, 3, expected.authorityVersion());

        assertTrue(signer.verify(moved, signature));
    }

    @Test
    void authorityVersionTamperStillInvalidatesSignatureAfterMovement() {
        ProjectionTokenSigner signer = signer();
        ExpectedProjection expected = expected(14, 7);
        byte[] signature = signer.sign(expected);

        ObservedProjection tampered = observed(expected, 2, expected.authorityVersion() + 1);

        assertFalse(signer.verify(tampered, signature));
    }

    @Test
    void definitionTamperStillInvalidatesSignature() {
        ProjectionTokenSigner signer = signer();
        ExpectedProjection expected = expected(5, 2);
        byte[] signature = signer.sign(expected);
        ObservedProjection tampered =
                new ObservedProjection(
                        1,
                        expected.valueId(),
                        DefinitionId.of("weapon.test.other"),
                        expected.valueType(),
                        expected.quantity(),
                        expected.authorityVersion(),
                        expected.displayRevision(),
                        expected.contentVersion(),
                        expected.testProvenance(),
                        false);

        assertFalse(signer.verify(tampered, signature));
    }

    private static ProjectionTokenSigner signer() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x5A);
        return new ProjectionTokenSigner(key);
    }

    private static ExpectedProjection expected(int slot, long version) {
        return new ExpectedProjection(
                UUID.randomUUID(),
                SWORD,
                ProjectionValueType.UNIQUE_ITEM,
                slot,
                1,
                version,
                3,
                "content.test.1",
                Optional.of("dev:test"));
    }

    private static ObservedProjection observed(
            ExpectedProjection expected, int slot, long authorityVersion) {
        return new ObservedProjection(
                slot,
                expected.valueId(),
                expected.definitionId(),
                expected.valueType(),
                expected.quantity(),
                authorityVersion,
                expected.displayRevision(),
                expected.contentVersion(),
                expected.testProvenance(),
                false);
    }
}
