package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.result.ErrorCode;

public enum PhysicalLotResolutionErrorCode implements ErrorCode {
    PHYSICAL_LOT_SLOT_NOT_GAMEPLAY,
    PHYSICAL_LOT_PROJECTION_INVALID,
    PHYSICAL_LOT_NOT_STACKABLE,
    PHYSICAL_LOT_PROJECTION_STALE,
    PHYSICAL_LOT_RECORD_MISSING,
    PHYSICAL_LOT_OWNER_MISMATCH,
    PHYSICAL_LOT_LOCATION_MISMATCH;

    @Override
    public String code() {
        return name();
    }
}
