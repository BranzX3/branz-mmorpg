package com.branz.mmorpg.items.asset;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.ItemDefinition;

public interface ItemAssetAdapter {
    Result<ItemPresentation, ItemAssetErrorCode> resolve(ItemDefinition definition);
}
