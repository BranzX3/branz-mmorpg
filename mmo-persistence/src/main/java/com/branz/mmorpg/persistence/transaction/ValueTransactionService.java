package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.result.Result;
import java.util.List;
import java.util.Optional;

public interface ValueTransactionService {
    Result<TransactionExecution, TransactionErrorCode> grantItem(
            TransactionRequest request, NewItemLocation item);

    Result<TransactionExecution, TransactionErrorCode> grantLot(
            TransactionRequest request, NewLotLocation lot);

    Result<TransactionExecution, TransactionErrorCode> moveItem(
            TransactionRequest request, ItemLocationMove move);

    Result<TransactionExecution, TransactionErrorCode> moveItemsAtomically(
            TransactionRequest request, List<ItemLocationMove> moves);

    Result<TransactionExecution, TransactionErrorCode> updateItemPayload(
            TransactionRequest request, ItemPayloadUpdate update);

    Result<TransactionExecution, TransactionErrorCode> moveLot(
            TransactionRequest request, LotLocationMove move);

    Result<TransactionExecution, TransactionErrorCode> transferLotQuantity(
            TransactionRequest request, LotQuantityTransfer transfer);

    Result<TransactionExecution, TransactionErrorCode> consumeLot(
            TransactionRequest request, LotQuantityConsumption consumption);

    Result<TransactionExecution, TransactionErrorCode> bindCrossbowBolt(
            TransactionRequest request, CrossbowBoltBinding binding);

    Result<Optional<ItemLocationRecord>, TransactionErrorCode> findItem(ItemId itemId);

    Result<Optional<LotLocationRecord>, TransactionErrorCode> findLot(LotId lotId);

    Result<List<ItemLocationRecord>, TransactionErrorCode> findItemsOwnedBy(
            CharacterId characterId);

    Result<List<LotLocationRecord>, TransactionErrorCode> findLotsOwnedBy(CharacterId characterId);
}
