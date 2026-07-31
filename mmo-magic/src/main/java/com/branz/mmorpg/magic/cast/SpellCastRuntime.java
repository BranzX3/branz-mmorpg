package com.branz.mmorpg.magic.cast;

import com.branz.mmorpg.combat.action.CombatResources;
import com.branz.mmorpg.magic.definition.SpellDefinition;
import java.util.Objects;
import java.util.OptionalLong;

/** Immutable server-tick spell cast with an explicit mana reservation/commit boundary. */
public record SpellCastRuntime(
        SpellDefinition spell,
        SpellCastPhase phase,
        long startedAtTick,
        OptionalLong releasedAtTick,
        boolean manaCommitted,
        CombatResources resources) {
    public SpellCastRuntime {
        Objects.requireNonNull(spell, "spell");
        Objects.requireNonNull(phase, "phase");
        if (startedAtTick < 0) {
            throw new IllegalArgumentException("startedAtTick must not be negative");
        }
        Objects.requireNonNull(releasedAtTick, "releasedAtTick");
        Objects.requireNonNull(resources, "resources");
        if (releasedAtTick.isPresent() != manaCommitted) {
            throw new IllegalArgumentException("release and mana commit must share one boundary");
        }
        if ((phase == SpellCastPhase.RECOVERY || phase == SpellCastPhase.COMPLETE)
                && !manaCommitted) {
            throw new IllegalArgumentException("recovery requires committed mana");
        }
    }

    public int chargeTicks(long currentTick) {
        long chargeStartedAt = startedAtTick + spell.phases().windupTicks();
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, currentTick - chargeStartedAt));
    }
}
