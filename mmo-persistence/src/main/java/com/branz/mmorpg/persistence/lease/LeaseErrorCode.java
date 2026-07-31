package com.branz.mmorpg.persistence.lease;

import com.branz.mmorpg.api.result.ErrorCode;

public enum LeaseErrorCode implements ErrorCode {
    LEASE_DATABASE_UNAVAILABLE,
    LEASE_NOT_FOUND,
    LEASE_OWNERSHIP_MISMATCH,
    LEASE_STALE_VERSION,
    LEASE_EXPIRED,
    LEASE_NOT_EXPIRED,
    LEASE_SESSION_COLLISION;

    @Override
    public String code() {
        return name();
    }
}
