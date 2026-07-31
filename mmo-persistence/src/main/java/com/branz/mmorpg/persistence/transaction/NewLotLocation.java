package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.LotId;
import java.util.Objects;
import java.util.Optional;

public record NewLotLocation(
        LotId lotId,
        DefinitionId definitionId,
        String variant,
        long quantity,
        Optional<CharacterId> ownerCharacterId,
        ValueLocation location,
        String lineageJson) {
    public NewLotLocation {
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(variant, "variant");
        if (variant.isBlank()) {
            throw new IllegalArgumentException("variant must not be blank");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        Objects.requireNonNull(ownerCharacterId, "ownerCharacterId");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(lineageJson, "lineageJson");
        if (lineageJson.isBlank()) {
            throw new IllegalArgumentException("lineageJson must not be blank");
        }
    }
}
