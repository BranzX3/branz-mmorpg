package com.branz.mmorpg.items.instance;

import java.util.Objects;
import java.util.Optional;

/** Typed authoritative location plus its slot/container reference. */
public record ItemLocation(ItemLocationType type, Optional<String> reference) {
    public ItemLocation {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reference, "reference");
        reference =
                reference.map(
                        value -> {
                            if (value.isBlank()) {
                                throw new IllegalArgumentException(
                                        "location reference must not be blank");
                            }
                            return value;
                        });
    }

    public static ItemLocation inventory(int slot) {
        if (slot < 0 || slot > 35) {
            throw new IllegalArgumentException("inventory slot must be between 0 and 35");
        }
        return new ItemLocation(
                slot < 9 ? ItemLocationType.HOTBAR : ItemLocationType.PLAYER_INVENTORY,
                Optional.of("slot:" + slot));
    }

    public static ItemLocation equipped(String slot, boolean virtual) {
        Objects.requireNonNull(slot, "slot");
        return new ItemLocation(
                virtual ? ItemLocationType.VIRTUAL_EQUIPPED : ItemLocationType.NATIVE_EQUIPPED,
                Optional.of(slot));
    }
}
