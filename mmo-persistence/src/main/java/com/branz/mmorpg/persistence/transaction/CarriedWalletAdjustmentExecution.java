package com.branz.mmorpg.persistence.transaction;

import java.util.Objects;

public record CarriedWalletAdjustmentExecution(
        CarriedWalletBalance balance, TransactionExecution transaction) {
    public CarriedWalletAdjustmentExecution {
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(transaction, "transaction");
    }
}
