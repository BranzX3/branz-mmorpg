package com.branz.mmorpg.magic.effect;

import com.branz.mmorpg.magic.definition.SpellDefinition;
import java.util.Objects;
import java.util.Optional;

/** Applies and consumes a bounded encounter-scoped Runic Imbuement. */
public final class RunicImbuementEngine {
    public RunicImbuementRuntime start(SpellDefinition spell, long currentTick) {
        Objects.requireNonNull(spell, "spell");
        if (currentTick < 0) {
            throw new IllegalArgumentException("currentTick must not be negative");
        }
        SpellDefinition.Imbuement profile = spell.imbuement().orElseThrow();
        return new RunicImbuementRuntime(
                spell,
                currentTick,
                currentTick + profile.durationTicks(),
                profile.maximumCharges());
    }

    public ImbuementHitResolution consume(RunicImbuementRuntime runtime, long currentTick) {
        Objects.requireNonNull(runtime, "runtime");
        if (!runtime.activeAt(currentTick)) {
            return new ImbuementHitResolution(false, Optional.empty());
        }
        int remaining = runtime.remainingCharges() - 1;
        return new ImbuementHitResolution(
                true,
                remaining == 0
                        ? Optional.empty()
                        : Optional.of(
                                new RunicImbuementRuntime(
                                        runtime.spell(),
                                        runtime.appliedAtTick(),
                                        runtime.expiresAtTick(),
                                        remaining)));
    }
}
