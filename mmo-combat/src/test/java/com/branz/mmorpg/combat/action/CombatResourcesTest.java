package com.branz.mmorpg.combat.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CombatResourcesTest {
    @Test
    void directStaminaSpendCannotConsumeReservedStamina() {
        CombatResources resources = new CombatResources(1000, 1000, 100, 80, 100, 100, 0, 30, 0);

        assertEquals(50, resources.availableStamina());
        assertTrue(resources.spendStamina(51).isEmpty());
        CombatResources spent = resources.spendStamina(50).orElseThrow();
        assertEquals(30, spent.stamina());
        assertEquals(30, spent.reservedStamina());
        assertEquals(0, spent.availableStamina());
    }
}
