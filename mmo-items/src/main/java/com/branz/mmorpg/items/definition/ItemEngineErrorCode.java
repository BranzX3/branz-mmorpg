package com.branz.mmorpg.items.definition;

import com.branz.mmorpg.api.result.ErrorCode;

public enum ItemEngineErrorCode implements ErrorCode {
    ITEM_ASSET_ID_INVALID,
    ITEM_CLASS_INVALID,
    ITEM_DURABILITY_INVALID;

    @Override
    public String code() {
        return name();
    }
}
