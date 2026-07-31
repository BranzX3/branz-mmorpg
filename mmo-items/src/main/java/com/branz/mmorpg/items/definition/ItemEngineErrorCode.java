package com.branz.mmorpg.items.definition;

import com.branz.mmorpg.api.result.ErrorCode;

public enum ItemEngineErrorCode implements ErrorCode {
    ITEM_ASSET_ID_INVALID,
    ITEM_CLASS_INVALID,
    ITEM_DURABILITY_INVALID,
    ITEM_WEAPON_PROFILE_INVALID,
    ITEM_AMMO_PROFILE_INVALID,
    ITEM_QUIVER_PROFILE_INVALID,
    ITEM_CATALYST_PROFILE_INVALID,
    ITEM_SHIELD_PROFILE_INVALID;

    @Override
    public String code() {
        return name();
    }
}
