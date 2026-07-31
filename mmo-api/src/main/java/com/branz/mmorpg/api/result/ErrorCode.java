package com.branz.mmorpg.api.result;

/**
 * A stable, machine-readable error contract.
 *
 * <p>Codes may be added, but an existing code must not be repurposed for a different failure.
 */
public interface ErrorCode {
    String code();
}
