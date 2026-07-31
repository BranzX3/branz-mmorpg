package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import java.util.Objects;
import java.util.Optional;

public record NewItemLocation(
        ItemId itemId,
        DefinitionId definitionId,
        Optional<CharacterId> ownerCharacterId,
        ValueLocation location,
        String payloadJson) {
    public NewItemLocation {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(ownerCharacterId, "ownerCharacterId");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(payloadJson, "payloadJson");
        if (payloadJson.isBlank()) {
            throw new IllegalArgumentException("payloadJson must not be blank");
        }
    }
}
