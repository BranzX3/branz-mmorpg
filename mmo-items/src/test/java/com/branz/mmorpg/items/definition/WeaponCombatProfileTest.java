package com.branz.mmorpg.items.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class WeaponCombatProfileTest {
    @Test
    void legacyConstructorsUseOneDurabilityPerSuccessfulAttack() {
        WeaponCombatProfile profile = new WeaponCombatProfile("SWORD", 100);

        assertEquals(1, profile.durabilityCostPerSuccessfulAttack());
    }

    @Test
    void authoredDurabilityCostMustBePositive() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WeaponCombatProfile(
                                "SWORD",
                                100,
                                0,
                                Optional.empty(),
                                Optional.empty(),
                                OffhandPolicy.ANY,
                                Optional.empty()));
    }
}
