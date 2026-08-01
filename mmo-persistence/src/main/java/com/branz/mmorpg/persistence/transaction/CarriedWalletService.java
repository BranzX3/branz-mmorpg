package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.result.Result;
import java.util.Optional;
import java.util.UUID;

public interface CarriedWalletService {
    Result<CarriedWalletBalance, TransactionErrorCode> balance(CharacterId characterId);

    Result<Optional<CarriedWalletOperation>, TransactionErrorCode> findOperation(UUID operationId);

    Result<CarriedWalletAdjustmentExecution, TransactionErrorCode> adjust(
            TransactionRequest request, CarriedWalletAdjustment adjustment);
}
