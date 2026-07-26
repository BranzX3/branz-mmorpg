package com.branz.mmorpg.api.stat;

import com.branz.mmorpg.api.skill.ResourceType;
import java.util.Objects;

/** Deterministic initialization and regeneration policy for one resource. */
public record ResourcePolicy(
        ResourceType resource,
        InitialValue initialValue,
        double regenerationPerSecond,
        double combatRegenerationFactor) {
    public enum InitialValue { EMPTY, FULL }

    public ResourcePolicy {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(initialValue, "initialValue");
        if (!Double.isFinite(regenerationPerSecond) || regenerationPerSecond < 0
                || !Double.isFinite(combatRegenerationFactor)
                || combatRegenerationFactor < 0 || combatRegenerationFactor > 1) {
            throw new IllegalArgumentException("invalid resource regeneration policy");
        }
    }

    public static ResourcePolicy standard(ResourceType resource) {
        return switch (resource) {
            case HEALTH -> new ResourcePolicy(resource, InitialValue.FULL, 1.0, 0.0);
            case MANA -> new ResourcePolicy(resource, InitialValue.FULL, 5.0, 0.25);
            case STAMINA -> new ResourcePolicy(resource, InitialValue.FULL, 10.0, 0.50);
            case RAGE -> new ResourcePolicy(resource, InitialValue.EMPTY, 0.0, 0.0);
            case ENERGY -> new ResourcePolicy(resource, InitialValue.FULL, 12.0, 1.0);
        };
    }
}
