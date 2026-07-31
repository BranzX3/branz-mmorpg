package com.branz.mmorpg.items.asset;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;

public record ItemPresentation(
        DefinitionId definitionId,
        DefinitionId assetId,
        String fallbackMaterialKey,
        String displayName) {
    public ItemPresentation {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(fallbackMaterialKey, "fallbackMaterialKey");
        Objects.requireNonNull(displayName, "displayName");
    }
}
