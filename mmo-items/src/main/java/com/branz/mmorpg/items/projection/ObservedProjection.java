package com.branz.mmorpg.items.projection;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Decoded MMO stack found in a Bukkit inventory. */
public record ObservedProjection(
        int slot,
        UUID valueId,
        DefinitionId definitionId,
        ProjectionValueType valueType,
        int quantity,
        long authorityVersion,
        long displayRevision,
        String contentVersion,
        Optional<String> testProvenance,
        boolean signatureValid) {
    public ObservedProjection {
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative");
        }
        Objects.requireNonNull(valueId, "valueId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(valueType, "valueType");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (authorityVersion < 1) {
            throw new IllegalArgumentException("authorityVersion must be positive");
        }
        if (displayRevision < 1) {
            throw new IllegalArgumentException("displayRevision must be positive");
        }
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(testProvenance, "testProvenance");
    }

    ExpectedProjection expectedForm() {
        return new ExpectedProjection(
                valueId,
                definitionId,
                valueType,
                slot,
                quantity,
                authorityVersion,
                displayRevision,
                contentVersion,
                testProvenance);
    }
}
