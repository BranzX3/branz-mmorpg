package com.branz.mmorpg.api.item;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable authoritative inventory, equipment, and overflow mailbox snapshot. */
public record InventorySnapshot(
        UUID playerId,
        int slotCapacity,
        Map<ContentId, Long> materials,
        Map<UUID, ItemInstance> items,
        Map<EquipmentSlot, UUID> equipped,
        Map<ContentId, Long> pendingMaterials,
        Map<UUID, ItemInstance> pendingItems,
        Instant updatedAt) {

    public InventorySnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(materials, "materials");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(equipped, "equipped");
        Objects.requireNonNull(pendingMaterials, "pendingMaterials");
        Objects.requireNonNull(pendingItems, "pendingItems");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (slotCapacity < 1) {
            throw new IllegalArgumentException("slotCapacity must be positive");
        }
        validateQuantities(materials);
        validateQuantities(pendingMaterials);
        materials = Map.copyOf(materials);
        items = Map.copyOf(items);
        equipped = Map.copyOf(equipped);
        pendingMaterials = Map.copyOf(pendingMaterials);
        pendingItems = Map.copyOf(pendingItems);
        if (!items.keySet().containsAll(equipped.values())) {
            throw new IllegalArgumentException("equipped item is not in owned inventory");
        }
    }

    public static InventorySnapshot empty(UUID playerId, int capacity, Instant now) {
        return new InventorySnapshot(playerId, capacity, Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), now);
    }

    private static void validateQuantities(Map<ContentId, Long> values) {
        values.forEach((id, quantity) -> {
            Objects.requireNonNull(id, "item definition");
            if (quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("inventory quantities must be positive");
            }
        });
    }
}
