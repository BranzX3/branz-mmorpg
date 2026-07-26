package com.branz.mmorpg.api.economy;

import com.branz.mmorpg.api.operation.OperationId;
import java.util.UUID;

/** Blocking external-currency port; Paper owns the BranzWallet adapter. */
public interface EconomyPaymentPort {
    long coins(UUID playerId);

    PaymentResult chargeCoins(
            UUID playerId, long amount, String purchaseId, OperationId operationId);

    enum Status { PAID, ALREADY_PAID, INSUFFICIENT, UNAVAILABLE, FAILED }

    record PaymentResult(Status status, String detail, long coinsCharged) {
        public boolean settled() {
            return status == Status.PAID || status == Status.ALREADY_PAID;
        }
    }
}
