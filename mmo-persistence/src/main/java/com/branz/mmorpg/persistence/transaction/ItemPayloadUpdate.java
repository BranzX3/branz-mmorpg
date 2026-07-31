package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.ItemId;
import java.util.Objects;
import java.util.Optional;

/** Compare-and-set replacement of one unique item's authoritative JSON payload. */
public record ItemPayloadUpdate(
        ItemId itemId,
        long expectedVersion,
        Optional<CharacterId> expectedOwnerCharacterId,
        ValueLocation expectedLocation,
        String expectedPayloadJson,
        String replacementPayloadJson) {
    public ItemPayloadUpdate {
        Objects.requireNonNull(itemId, "itemId");
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("expectedVersion must be positive");
        }
        Objects.requireNonNull(expectedOwnerCharacterId, "expectedOwnerCharacterId");
        Objects.requireNonNull(expectedLocation, "expectedLocation");
        expectedPayloadJson = requireJson(expectedPayloadJson, "expectedPayloadJson");
        replacementPayloadJson = requireJson(replacementPayloadJson, "replacementPayloadJson");
    }

    private static String requireJson(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
