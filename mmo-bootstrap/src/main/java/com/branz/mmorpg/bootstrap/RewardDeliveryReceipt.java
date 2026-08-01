package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.util.Objects;

record RewardDeliveryReceipt(TransactionId transactionId, ValueLocation destination) {
    RewardDeliveryReceipt {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(destination, "destination");
    }
}
