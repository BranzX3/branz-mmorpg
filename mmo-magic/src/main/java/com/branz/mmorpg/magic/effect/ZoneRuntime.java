package com.branz.mmorpg.magic.effect;

import com.branz.mmorpg.magic.definition.SpellDefinition;
import java.util.Objects;

/** Geometry-free authoritative lifetime and pulse schedule for one committed zone. */
public record ZoneRuntime(
        SpellDefinition spell,
        long startedAtTick,
        long expiresAtTick,
        long nextPulseAtTick,
        int pulsesEmitted,
        boolean expired) {
    public ZoneRuntime {
        Objects.requireNonNull(spell, "spell");
        if (spell.zone().isEmpty()
                || startedAtTick < 0
                || expiresAtTick <= startedAtTick
                || nextPulseAtTick < startedAtTick
                || pulsesEmitted < 0
                || expired && nextPulseAtTick < expiresAtTick) {
            throw new IllegalArgumentException("invalid zone runtime");
        }
    }
}
