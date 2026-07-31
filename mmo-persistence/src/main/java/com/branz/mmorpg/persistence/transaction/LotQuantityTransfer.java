package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.LotId;
import java.util.Objects;
import java.util.Optional;

/** Atomic full-lot move or child-lot split through one equipped Quiver capacity lock. */
public record LotQuantityTransfer(
        LotId sourceLotId,
        LotId destinationLotId,
        long expectedSourceVersion,
        long expectedSourceQuantity,
        Optional<CharacterId> expectedOwnerCharacterId,
        ValueLocation expectedSourceLocation,
        ValueLocation destinationLocation,
        long quantity,
        ItemId quiverItemId,
        long expectedQuiverVersion,
        Optional<CharacterId> expectedQuiverOwnerCharacterId,
        ValueLocation expectedQuiverLocation,
        long quiverCapacity) {
    public LotQuantityTransfer {
        Objects.requireNonNull(sourceLotId, "sourceLotId");
        Objects.requireNonNull(destinationLotId, "destinationLotId");
        if (expectedSourceVersion < 1
                || expectedSourceQuantity < 1
                || quantity < 1
                || quantity > expectedSourceQuantity) {
            throw new IllegalArgumentException("invalid source version or transfer quantity");
        }
        Objects.requireNonNull(expectedOwnerCharacterId, "expectedOwnerCharacterId");
        Objects.requireNonNull(expectedSourceLocation, "expectedSourceLocation");
        Objects.requireNonNull(destinationLocation, "destinationLocation");
        Objects.requireNonNull(quiverItemId, "quiverItemId");
        if (expectedQuiverVersion < 1 || quiverCapacity < 1 || quiverCapacity > 4096) {
            throw new IllegalArgumentException("invalid Quiver version or capacity");
        }
        Objects.requireNonNull(expectedQuiverOwnerCharacterId, "expectedQuiverOwnerCharacterId");
        if (!expectedOwnerCharacterId.equals(expectedQuiverOwnerCharacterId)) {
            throw new IllegalArgumentException("lot and Quiver must have the same owner");
        }
        Objects.requireNonNull(expectedQuiverLocation, "expectedQuiverLocation");
        ValueLocation quiverLocation = ValueLocation.quiver(quiverItemId);
        boolean stores = destinationLocation.equals(quiverLocation);
        boolean withdraws = expectedSourceLocation.equals(quiverLocation);
        if (stores == withdraws
                || (stores
                        && expectedSourceLocation.type() != ValueLocationType.CHARACTER_INVENTORY)
                || (withdraws
                        && destinationLocation.type() != ValueLocationType.CHARACTER_INVENTORY)) {
            throw new IllegalArgumentException(
                    "transfer must move between character inventory and the locked Quiver");
        }
        if (!expectedQuiverLocation.equals(ValueLocation.virtualEquipped("QUIVER"))) {
            throw new IllegalArgumentException("capacity container must be equipped as QUIVER");
        }
        boolean fullLot = quantity == expectedSourceQuantity;
        if (fullLot != sourceLotId.equals(destinationLotId)) {
            throw new IllegalArgumentException(
                    "full moves retain lot UUID and partial moves require a child lot UUID");
        }
    }

    public boolean storesInQuiver() {
        return destinationLocation.type() == ValueLocationType.QUIVER;
    }

    public boolean fullLot() {
        return quantity == expectedSourceQuantity;
    }
}
