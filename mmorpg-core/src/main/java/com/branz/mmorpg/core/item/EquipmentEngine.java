package com.branz.mmorpg.core.item;

import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.item.EquipmentSlot;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.item.ItemCategory;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Objects;
import java.util.UUID;

/** Pure equipment validation and atomic snapshot transformation. */
public final class EquipmentEngine {

    public InventorySnapshot equip(InventorySnapshot before, UUID itemId,
                                   EquipmentSlot slot, ContentSnapshot content, Instant now) {
        var item = before.items().get(itemId);
        if (item == null) throw new IllegalArgumentException("item is not owned " + itemId);
        if (item.boundOwner().isPresent() && !item.boundOwner().get().equals(before.playerId())) {
            throw new IllegalArgumentException("item is bound to another player");
        }
        var equipped = new EnumMap<EquipmentSlot, UUID>(EquipmentSlot.class);
        equipped.putAll(before.equipped());
        if (item.category() == ItemCategory.WEAPON) {
            if (slot != EquipmentSlot.MAIN_HAND) {
                throw new IllegalArgumentException("weapon requires MAIN_HAND");
            }
            var weapon = content.weapons().get(item.definitionId());
            if (weapon == null) {
                throw new IllegalArgumentException("missing weapon definition " + item.definitionId());
            }
            if (weapon.twoHanded()) equipped.remove(EquipmentSlot.OFF_HAND);
        } else if (!compatible(item.category(), slot)) {
            throw new IllegalArgumentException(item.category() + " is incompatible with " + slot);
        }
        equipped.values().removeIf(itemId::equals);
        equipped.put(slot, itemId);
        validateTwoHanded(equipped, before, content);
        return copy(before, equipped, now);
    }

    public InventorySnapshot unequip(InventorySnapshot before, EquipmentSlot slot, Instant now) {
        var equipped = new EnumMap<EquipmentSlot, UUID>(EquipmentSlot.class);
        equipped.putAll(before.equipped());
        equipped.remove(slot);
        return copy(before, equipped, now);
    }

    private static void validateTwoHanded(
            EnumMap<EquipmentSlot, UUID> equipped, InventorySnapshot inventory,
            ContentSnapshot content) {
        UUID main = equipped.get(EquipmentSlot.MAIN_HAND);
        if (main == null || !equipped.containsKey(EquipmentSlot.OFF_HAND)) return;
        var item = inventory.items().get(main);
        var weapon = item == null ? null : content.weapons().get(item.definitionId());
        if (weapon != null && weapon.twoHanded()) {
            throw new IllegalArgumentException("two-handed weapon reserves OFF_HAND");
        }
    }

    private static boolean compatible(ItemCategory category, EquipmentSlot slot) {
        return switch (category) {
            case ARMOR -> slot == EquipmentSlot.HELMET || slot == EquipmentSlot.CHEST
                    || slot == EquipmentSlot.BOOTS;
            case ACCESSORY -> slot == EquipmentSlot.ACCESSORY_1
                    || slot == EquipmentSlot.ACCESSORY_2;
            case CONSUMABLE -> slot == EquipmentSlot.CONSUMABLE;
            default -> false;
        };
    }

    private static InventorySnapshot copy(
            InventorySnapshot before, EnumMap<EquipmentSlot, UUID> equipped, Instant now) {
        Objects.requireNonNull(now, "now");
        return new InventorySnapshot(before.playerId(), before.slotCapacity(), before.materials(),
                before.items(), equipped, before.pendingMaterials(), before.pendingItems(), now);
    }
}
