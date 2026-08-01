package com.branz.mmorpg.combat.resource;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Current reusable Flask charges; allocation changes only through Rest preparation. */
public record FlaskState(FlaskAllocation allocation, Map<FlaskDose, Integer> charges) {
    public FlaskState {
        Objects.requireNonNull(allocation, "allocation");
        Objects.requireNonNull(charges, "charges");
        EnumMap<FlaskDose, Integer> copy = new EnumMap<>(FlaskDose.class);
        for (FlaskDose dose : FlaskDose.values()) {
            int charge = charges.getOrDefault(dose, 0);
            if (charge < 0 || charge > allocation.maximum(dose)) {
                throw new IllegalArgumentException("Flask charges exceed the allocation");
            }
            copy.put(dose, charge);
        }
        charges = Map.copyOf(copy);
    }

    public static FlaskState empty(FlaskAllocation allocation) {
        return new FlaskState(allocation, Map.of());
    }

    public static FlaskState full(FlaskAllocation allocation) {
        return new FlaskState(allocation, allocation.doses());
    }

    public int charge(FlaskDose dose) {
        return charges.get(Objects.requireNonNull(dose, "dose"));
    }

    public int totalCharges() {
        return charges.values().stream().mapToInt(Integer::intValue).sum();
    }
}
