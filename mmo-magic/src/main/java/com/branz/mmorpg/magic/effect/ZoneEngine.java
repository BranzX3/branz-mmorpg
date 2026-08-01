package com.branz.mmorpg.magic.effect;

import com.branz.mmorpg.magic.definition.SpellDefinition;
import java.util.Objects;

/** Advances a bounded zone without owning Bukkit geometry or target selection. */
public final class ZoneEngine {
    public ZoneRuntime start(SpellDefinition spell, long currentTick) {
        Objects.requireNonNull(spell, "spell");
        if (currentTick < 0) {
            throw new IllegalArgumentException("currentTick must not be negative");
        }
        SpellDefinition.Zone zone = spell.zone().orElseThrow();
        return new ZoneRuntime(
                spell, currentTick, currentTick + zone.durationTicks(), currentTick, 0, false);
    }

    public ZoneTickResolution advance(ZoneRuntime runtime, long currentTick) {
        Objects.requireNonNull(runtime, "runtime");
        if (currentTick < runtime.startedAtTick()) {
            throw new IllegalArgumentException("currentTick precedes zone start");
        }
        if (runtime.expired()) {
            return new ZoneTickResolution(runtime, false);
        }
        if (currentTick >= runtime.expiresAtTick()) {
            return new ZoneTickResolution(
                    new ZoneRuntime(
                            runtime.spell(),
                            runtime.startedAtTick(),
                            runtime.expiresAtTick(),
                            runtime.expiresAtTick(),
                            runtime.pulsesEmitted(),
                            true),
                    false);
        }
        if (currentTick < runtime.nextPulseAtTick()) {
            return new ZoneTickResolution(runtime, false);
        }
        int interval = runtime.spell().zone().orElseThrow().pulseIntervalTicks();
        return new ZoneTickResolution(
                new ZoneRuntime(
                        runtime.spell(),
                        runtime.startedAtTick(),
                        runtime.expiresAtTick(),
                        currentTick + interval,
                        runtime.pulsesEmitted() + 1,
                        false),
                true);
    }
}
