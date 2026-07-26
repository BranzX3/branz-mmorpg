package com.branz.mmorpg.api.error;

/**
 * Structured failure categories.
 *
 * <p>Every failure surfaced to an operator or to another subsystem carries one
 * of these rather than a bare message, so diagnostics can be matched, counted,
 * and translated without parsing prose.
 */
public enum ErrorCode {

    /** A required service could not start, or is not READY when called. */
    SERVICE_UNAVAILABLE,

    /** An illegal lifecycle transition was attempted. */
    SERVICE_LIFECYCLE,

    /** Caller supplied a value that violates a documented contract. */
    INVALID_ARGUMENT,

    /** Content failed to parse, validate, or resolve its references. */
    CONTENT_INVALID,

    /** Persistence failed; the caller must fail closed, not substitute a default. */
    STORAGE_FAILURE,

    /** A player profile could not be loaded; gameplay mutation stays disabled. */
    PROFILE_LOAD_FAILED,

    /** The operation was already applied under the same operation ID. */
    OPERATION_ALREADY_APPLIED,

    /** An external plugin surface (wallet, warehouse) was absent or refused. */
    EXTERNAL_SERVICE_UNAVAILABLE,

    /** The server is shutting down and refuses new mutations. */
    SHUTTING_DOWN;
}
