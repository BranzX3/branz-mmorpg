package com.branz.mmorpg.items.projection;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Authoritative database value that should have exactly one inventory projection. */
public record ExpectedProjection(
        UUID valueId,
        DefinitionId definitionId,
        ProjectionValueType valueType,
        int slot,
        int quantity,
        long authorityVersion,
        long displayRevision,
        String contentVersion,
        Optional<String> testProvenance) {
    public ExpectedProjection {
        Objects.requireNonNull(valueId, "valueId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(valueType, "valueType");
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (valueType == ProjectionValueType.UNIQUE_ITEM && quantity != 1) {
            throw new IllegalArgumentException("unique item projection quantity must be one");
        }
        if (authorityVersion < 1) {
            throw new IllegalArgumentException("authorityVersion must be positive");
        }
        if (displayRevision < 1) {
            throw new IllegalArgumentException("displayRevision must be positive");
        }
        Objects.requireNonNull(contentVersion, "contentVersion");
        if (contentVersion.isBlank()) {
            throw new IllegalArgumentException("contentVersion must not be blank");
        }
        Objects.requireNonNull(testProvenance, "testProvenance");
        testProvenance =
                testProvenance.map(
                        value -> {
                            if (value.isBlank()) {
                                throw new IllegalArgumentException(
                                        "testProvenance must not be blank");
                            }
                            return value;
                        });
    }
}
