package com.branz.mmorpg.items.asset;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.ItemDefinition;
import java.util.Objects;

/** Safe barrier representation used when the external asset provider is unavailable. */
public final class FallbackItemAssetAdapter implements ItemAssetAdapter {
    @Override
    public Result<ItemPresentation, ItemAssetErrorCode> resolve(ItemDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return Result.success(
                new ItemPresentation(
                        definition.id(),
                        definition.assetId(),
                        "minecraft:barrier",
                        definition.id().value()));
    }
}
