package com.branz.mmorpg.api.economy;

import java.util.UUID;

/** Wallet-owned idempotent administrative currency mutation. */
public interface AdminCurrencyPort {
    boolean adjustCredits(UUID playerId, long amount, String operationId, String reason);
}
