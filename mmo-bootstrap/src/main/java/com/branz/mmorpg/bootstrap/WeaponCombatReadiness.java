package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.items.definition.ItemDefinition;
import java.util.Objects;
import java.util.Optional;

/** Pure readiness rule for authoritative durable weapon state. */
final class WeaponCombatReadiness {
    static final String BROKEN = "Combat not ready: equipped weapon is broken.";
    static final String INVALID = "Combat not ready: weapon durability state is invalid.";

    private WeaponCombatReadiness() {}

    static Optional<String> durabilityFailure(ItemDefinition weapon, String payloadJson) {
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(payloadJson, "payloadJson");
        if (weapon.baseMaxDurability().isEmpty()) {
            return Optional.empty();
        }
        try {
            WeaponDurability durability =
                    WeaponPayloadCodec.decode(
                            payloadJson, weapon.baseMaxDurability().getAsInt());
            return durability.broken() ? Optional.of(BROKEN) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.of(INVALID);
        }
    }
}
