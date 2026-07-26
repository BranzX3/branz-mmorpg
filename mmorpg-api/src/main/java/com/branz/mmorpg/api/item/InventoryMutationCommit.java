package com.branz.mmorpg.api.item;

public record InventoryMutationCommit(
        boolean applied,
        InventorySnapshot before,
        InventorySnapshot after,
        long delivered,
        long overflowed) {
}
