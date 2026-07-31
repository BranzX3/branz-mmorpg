package com.branz.mmorpg.persistence.migration;

import com.branz.mmorpg.api.result.ErrorCode;

public enum MigrationErrorCode implements ErrorCode {
    MIGRATION_CATALOG_INVALID,
    MIGRATION_DATABASE_UNAVAILABLE,
    MIGRATION_LOCK_FAILED,
    MIGRATION_CHECKSUM_MISMATCH,
    MIGRATION_UNKNOWN_APPLIED,
    MIGRATION_APPLY_FAILED;

    @Override
    public String code() {
        return name();
    }
}
