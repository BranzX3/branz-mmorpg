package com.branz.mmorpg.combat.resource;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Rest-authored allocation of every reusable Expedition Flask charge. */
public record FlaskAllocation(int capacity, Map<FlaskDose, Integer> doses) {
    public static final int BASE_CAPACITY = 5;

    public FlaskAllocation {
        if (capacity < 1 || capacity > 32) {
            throw new IllegalArgumentException("capacity must be between 1 and 32");
        }
        Objects.requireNonNull(doses, "doses");
        EnumMap<FlaskDose, Integer> copy = new EnumMap<>(FlaskDose.class);
        for (FlaskDose dose : FlaskDose.values()) {
            int allocation = doses.getOrDefault(dose, 0);
            if (allocation < 0) {
                throw new IllegalArgumentException("dose allocation must not be negative");
            }
            copy.put(dose, allocation);
        }
        if (copy.values().stream().mapToInt(Integer::intValue).sum() != capacity) {
            throw new IllegalArgumentException("every Flask capacity slot must be allocated");
        }
        doses = Map.copyOf(copy);
    }

    public static FlaskAllocation balanced() {
        return new FlaskAllocation(
                BASE_CAPACITY,
                Map.of(FlaskDose.HEALING, 3, FlaskDose.MANA, 1, FlaskDose.STAMINA, 1));
    }

    public int maximum(FlaskDose dose) {
        return doses.get(Objects.requireNonNull(dose, "dose"));
    }
}
