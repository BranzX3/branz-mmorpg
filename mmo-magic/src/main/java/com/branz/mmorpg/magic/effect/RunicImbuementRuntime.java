package com.branz.mmorpg.magic.effect;

import com.branz.mmorpg.magic.definition.SpellDefinition;
import java.util.Objects;

/** Encounter-scoped weapon coating with bounded duration and hit charges. */
public record RunicImbuementRuntime(
        SpellDefinition spell, long appliedAtTick, long expiresAtTick, int remainingCharges) {
    public RunicImbuementRuntime {
        Objects.requireNonNull(spell, "spell");
        if (spell.imbuement().isEmpty()
                || appliedAtTick < 0
                || expiresAtTick <= appliedAtTick
                || remainingCharges < 0
                || remainingCharges > spell.imbuement().orElseThrow().maximumCharges()) {
            throw new IllegalArgumentException("invalid Runic Imbuement runtime");
        }
    }

    public boolean activeAt(long currentTick) {
        return remainingCharges > 0 && currentTick < expiresAtTick;
    }
}
