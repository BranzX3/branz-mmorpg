package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.combat.health.CombatHealthEngine;
import com.branz.mmorpg.combat.health.CombatHealthProfile;
import com.branz.mmorpg.combat.health.CombatHealthRuntime;
import org.junit.jupiter.api.Test;

class MeleeTargetDamageCoordinatorTest {
    private final CombatHealthEngine health =
            new CombatHealthEngine(CombatHealthProfile.trainingEnemy());

    @Test
    void nonLethalDamageReducesAuthoritativeHealthAndEmitsFeedback() {
        CombatHealthRuntime full = CombatHealthRuntime.full(health.profile(), 10);

        MeleeTargetDamageCoordinator.MeleeTargetDamageResult result =
                MeleeTargetDamageCoordinator.apply(health, full, 11, 25.0);

        assertEquals(25.0, result.appliedDamage());
        assertEquals(health.profile().maximum() - 25.0, result.runtime().current());
        assertFalse(result.lethalNow());
        assertTrue(result.feedback().isPresent());
    }

    @Test
    void overkillAppliesOnlyRemainingHealthAndIsLethal() {
        CombatHealthRuntime current = new CombatHealthRuntime(12.0, 10, 10);

        MeleeTargetDamageCoordinator.MeleeTargetDamageResult result =
                MeleeTargetDamageCoordinator.apply(health, current, 11, 999.0);

        assertEquals(12.0, result.appliedDamage());
        assertEquals(0.0, result.runtime().current());
        assertTrue(result.lethalNow());
        assertTrue(result.feedback().isPresent());
    }

    @Test
    void zeroDamagePreservesHealthAndEmitsNoFalseHitConfirmation() {
        CombatHealthRuntime full = CombatHealthRuntime.full(health.profile(), 10);

        MeleeTargetDamageCoordinator.MeleeTargetDamageResult result =
                MeleeTargetDamageCoordinator.apply(health, full, 11, 0.0);

        assertEquals(0.0, result.appliedDamage());
        assertEquals(full.current(), result.runtime().current());
        assertFalse(result.lethalNow());
        assertTrue(result.feedback().isEmpty());
    }
}
