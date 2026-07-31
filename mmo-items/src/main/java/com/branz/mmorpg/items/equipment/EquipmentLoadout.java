package com.branz.mmorpg.items.equipment;

import com.branz.mmorpg.api.identity.ItemId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class EquipmentLoadout {
    private final Map<EquipmentSlot, ItemId> equipped;

    private EquipmentLoadout(Map<EquipmentSlot, ItemId> equipped) {
        this.equipped = Map.copyOf(equipped);
    }

    public static EquipmentLoadout empty() {
        return new EquipmentLoadout(Map.of());
    }

    public Optional<ItemId> item(EquipmentSlot slot) {
        return Optional.ofNullable(equipped.get(Objects.requireNonNull(slot, "slot")));
    }

    public Map<EquipmentSlot, ItemId> equipped() {
        return equipped;
    }

    public EquipmentLoadout with(EquipmentSlot slot, Optional<ItemId> itemId) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(itemId, "itemId");
        EnumMap<EquipmentSlot, ItemId> updated = new EnumMap<>(EquipmentSlot.class);
        updated.putAll(equipped);
        if (itemId.isPresent()) {
            updated.put(slot, itemId.orElseThrow());
        } else {
            updated.remove(slot);
        }
        return new EquipmentLoadout(updated);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof EquipmentLoadout loadout && equipped.equals(loadout.equipped);
    }

    @Override
    public int hashCode() {
        return equipped.hashCode();
    }

    @Override
    public String toString() {
        return equipped.toString();
    }
}
