package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.result.Result;

public interface CarriedWalletService {
    Result<CarriedWalletBalance, TransactionErrorCode> balance(CharacterId characterId);

    Result<CarriedWalletAdjustmentExecution, TransactionErrorCode> adjust(
            TransactionRequest request, CarriedWalletAdjustment adjustment);
}
