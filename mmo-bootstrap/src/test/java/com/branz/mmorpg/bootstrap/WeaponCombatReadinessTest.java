package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.WeaponCombatProfile;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class WeaponCombatReadinessTest {
    @Test
    void freshDurableWeaponIsReadyAtDefinitionMaximum() {
        assertTrue(WeaponCombatReadiness.durabilityFailure(sword(3), "{}").isEmpty());
    }

    @Test
    void brokenWeaponFailsClosed() {
        String payload = WeaponPayloadCodec.encode("{}", new WeaponDurability(0, 3));

        assertEquals(
                Optional.of(WeaponCombatReadiness.BROKEN),
                WeaponCombatReadiness.durabilityFailure(sword(3), payload));
    }

    @Test
    void malformedOrMismatchedDurabilityFailsClosed() {
        assertEquals(
                Optional.of(WeaponCombatReadiness.INVALID),
                WeaponCombatReadiness.durabilityFailure(
                        sword(3),
                        "{\"durability\":{\"currentDurability\":1,\"maximumDurability\":4}}"));
    }

    @Test
    void weaponWithoutDurabilityHasNoDurabilityGate() {
        ItemDefinition weapon =
                new ItemDefinition(
                        DefinitionId.of("weapon.test_indestructible"),
                        DefinitionId.of("asset.weapon.test_indestructible"),
                        ItemClass.UNIQUE_DURABLE,
                        OptionalInt.empty(),
                        false,
                        Optional.of(new WeaponCombatProfile("SWORD", 100)));

        assertTrue(WeaponCombatReadiness.durabilityFailure(weapon, "{}").isEmpty());
    }

    private static ItemDefinition sword(int maximumDurability) {
        return new ItemDefinition(
                DefinitionId.of("weapon.test_sword"),
                DefinitionId.of("asset.weapon.test_sword"),
                ItemClass.UNIQUE_DURABLE,
                OptionalInt.of(maximumDurability),
                false,
                Optional.of(new WeaponCombatProfile("SWORD", 100)));
    }
}
