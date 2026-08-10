package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.result.ErrorCode;

enum PhysicalItemResolutionErrorCode implements ErrorCode {
    PHYSICAL_ITEM_SLOT_NOT_GAMEPLAY,
    PHYSICAL_ITEM_NOT_MMO_PROJECTION,
    PHYSICAL_ITEM_PROJECTION_INVALID,
    PHYSICAL_ITEM_NOT_UNIQUE,
    PHYSICAL_ITEM_PROJECTION_STALE,
    PHYSICAL_ITEM_RECORD_MISSING,
    PHYSICAL_ITEM_OWNER_MISMATCH,
    PHYSICAL_ITEM_LOCATION_MISMATCH,
    PHYSICAL_ITEM_DEFINITION_MISSING;

    @Override
    public String code() {
        return name();
    }
}
