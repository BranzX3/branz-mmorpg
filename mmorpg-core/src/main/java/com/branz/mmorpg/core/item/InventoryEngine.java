package com.branz.mmorpg.core.item;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.item.ItemInstance;
import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** Pure inventory capacity and overflow rules. */
public final class InventoryEngine {

    public record Mutation(InventorySnapshot snapshot, long delivered, long overflowed) {}

    public Mutation grantMaterial(InventorySnapshot before, MaterialDefinition definition,
                                  long quantity, ToIntFunction<ContentId> stackSize,
                                  Instant now) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(definition, "definition");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        long current = before.materials().getOrDefault(definition.id(), 0L);
        int usedOther = usedSlotsExcluding(before, definition.id(), stackSize);
        long maximumForMaterial = Math.multiplyExact(
                (long) Math.max(0, before.slotCapacity() - usedOther),
                definition.maxStackSize());
        long delivered = Math.min(quantity, Math.max(0L, maximumForMaterial - current));
        long overflow = quantity - delivered;
        var materials = new HashMap<>(before.materials());
        if (delivered > 0) {
            materials.put(definition.id(), Math.addExact(current, delivered));
        }
        var pending = new HashMap<>(before.pendingMaterials());
        if (overflow > 0) {
            pending.merge(definition.id(), overflow, Math::addExact);
        }
        return new Mutation(new InventorySnapshot(before.playerId(), before.slotCapacity(),
                materials, before.items(), before.equipped(), pending, before.pendingItems(), now),
                delivered, overflow);
    }

    public Mutation grantUnique(InventorySnapshot before, ItemInstance item,
                                ToIntFunction<ContentId> stackSize, Instant now) {
        Objects.requireNonNull(item, "item");
        if (before.items().containsKey(item.instanceId())
                || before.pendingItems().containsKey(item.instanceId())) {
            throw new IllegalArgumentException("duplicate item instance " + item.instanceId());
        }
        boolean hasSpace = usedSlots(before, stackSize) < before.slotCapacity();
        var items = new HashMap<>(before.items());
        var pending = new HashMap<>(before.pendingItems());
        if (hasSpace) {
            items.put(item.instanceId(), item);
        } else {
            pending.put(item.instanceId(), item);
        }
        return new Mutation(new InventorySnapshot(before.playerId(), before.slotCapacity(),
                before.materials(), items, before.equipped(), before.pendingMaterials(), pending,
                now), hasSpace ? 1 : 0, hasSpace ? 0 : 1);
    }

    public Mutation claimMaterial(InventorySnapshot before, MaterialDefinition definition,
                                  long requested, ToIntFunction<ContentId> stackSize,
                                  Instant now) {
        if (requested <= 0) throw new IllegalArgumentException("quantity must be positive");
        long pendingQuantity = before.pendingMaterials().getOrDefault(definition.id(), 0L);
        if (pendingQuantity == 0) {
            throw new IllegalArgumentException("material is not pending " + definition.id());
        }
        long current = before.materials().getOrDefault(definition.id(), 0L);
        int usedOther = usedSlotsExcluding(before, definition.id(), stackSize);
        long maximumForMaterial = Math.multiplyExact(
                (long) Math.max(0, before.slotCapacity() - usedOther),
                definition.maxStackSize());
        long delivered = Math.min(Math.min(requested, pendingQuantity),
                Math.max(0L, maximumForMaterial - current));
        if (delivered == 0) throw new IllegalStateException("no inventory space");
        var materials = new HashMap<>(before.materials());
        materials.put(definition.id(), Math.addExact(current, delivered));
        var pending = new HashMap<>(before.pendingMaterials());
        long remaining = pendingQuantity - delivered;
        if (remaining == 0) pending.remove(definition.id());
        else pending.put(definition.id(), remaining);
        return new Mutation(new InventorySnapshot(before.playerId(), before.slotCapacity(),
                materials, before.items(), before.equipped(), pending, before.pendingItems(), now),
                delivered, 0);
    }

    public Mutation claimUnique(InventorySnapshot before, UUID itemId,
                                ToIntFunction<ContentId> stackSize, Instant now) {
        ItemInstance item = before.pendingItems().get(itemId);
        if (item == null) throw new IllegalArgumentException("item is not pending " + itemId);
        if (usedSlots(before, stackSize) >= before.slotCapacity()) {
            throw new IllegalStateException("no inventory space");
        }
        var items = new HashMap<>(before.items());
        items.put(itemId, item);
        var pending = new HashMap<>(before.pendingItems());
        pending.remove(itemId);
        return new Mutation(new InventorySnapshot(before.playerId(), before.slotCapacity(),
                before.materials(), items, before.equipped(), before.pendingMaterials(), pending,
                now), 1, 0);
    }

    public Mutation revokeMaterial(
            InventorySnapshot before, ContentId materialId, long quantity, Instant now) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        long current = before.materials().getOrDefault(materialId, 0L);
        if (current < quantity) throw new IllegalStateException("insufficient material");
        var materials = new HashMap<>(before.materials());
        long remaining = current - quantity;
        if (remaining == 0) materials.remove(materialId);
        else materials.put(materialId, remaining);
        return new Mutation(new InventorySnapshot(before.playerId(), before.slotCapacity(),
                materials, before.items(), before.equipped(), before.pendingMaterials(),
                before.pendingItems(), now), quantity, 0);
    }

    public Mutation revokeUnique(
            InventorySnapshot before, UUID itemId, Instant now) {
        if (before.equipped().containsValue(itemId)) {
            throw new IllegalStateException("equipped item must be unequipped first");
        }
        var items = new HashMap<>(before.items());
        var pending = new HashMap<>(before.pendingItems());
        if (items.remove(itemId) == null && pending.remove(itemId) == null) {
            throw new IllegalArgumentException("unknown owned item " + itemId);
        }
        return new Mutation(new InventorySnapshot(before.playerId(), before.slotCapacity(),
                before.materials(), items, before.equipped(), before.pendingMaterials(),
                pending, now), 1, 0);
    }

    public int usedSlots(InventorySnapshot inventory, ToIntFunction<ContentId> stackSize) {
        long materialSlots = inventory.materials().entrySet().stream()
                .mapToLong(entry -> stackCount(entry.getValue(),
                        validStackSize(stackSize.applyAsInt(entry.getKey()))))
                .sum();
        return Math.toIntExact(materialSlots + inventory.items().size());
    }

    private int usedSlotsExcluding(InventorySnapshot inventory, ContentId excluded,
                                   ToIntFunction<ContentId> stackSize) {
        long materialSlots = inventory.materials().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(excluded))
                .mapToLong(entry -> stackCount(entry.getValue(),
                        validStackSize(stackSize.applyAsInt(entry.getKey()))))
                .sum();
        return Math.toIntExact(materialSlots + inventory.items().size());
    }

    private static int validStackSize(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("unknown material in inventory");
        }
        return value;
    }

    private static long stackCount(long quantity, int stackSize) {
        return (quantity + stackSize - 1L) / stackSize;
    }
}
