package com.branz.mmorpg.combat.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
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

    @Test
    void publicManaReservationCommitsOrReleasesExactly() {
        CombatResources full = CombatResources.full(1000, 100, 100);
        CombatResources reserved =
                ((Result.Success<CombatResources, ActionTimelineErrorCode>) full.reserveMana(18))
                        .value();

        assertEquals(100, reserved.mana());
        assertEquals(18, reserved.reservedMana());
        CombatResources committed = reserved.commitReservedMana(18);
        assertEquals(82, committed.mana());
        assertEquals(0, committed.reservedMana());
        CombatResources released = reserved.releaseReservedMana(18);
        assertEquals(100, released.mana());
        assertEquals(0, released.reservedMana());
        assertEquals(100, committed.restoreMana(50).mana());
    }
}
