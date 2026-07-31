package com.branz.mmorpg.items.asset;

import com.branz.mmorpg.api.result.ErrorCode;

public enum ItemAssetErrorCode implements ErrorCode {
    ITEM_ASSET_UNAVAILABLE;

    @Override
    public String code() {
        return name();
    }
}
