package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.combat.guard.CombatDefenseOutcome;
import org.junit.jupiter.api.Test;

class ShieldImpactWearPolicyTest {
    @Test
    void onlyGuardedImpactsConsumeShieldDurability() {
        assertTrue(ShieldImpactWearPolicy.consumesDurability(CombatDefenseOutcome.PERFECT_GUARD));
        assertTrue(ShieldImpactWearPolicy.consumesDurability(CombatDefenseOutcome.GUARDED));
        assertTrue(ShieldImpactWearPolicy.consumesDurability(CombatDefenseOutcome.GUARD_BREAK));
        assertFalse(ShieldImpactWearPolicy.consumesDurability(CombatDefenseOutcome.DODGED));
        assertFalse(ShieldImpactWearPolicy.consumesDurability(CombatDefenseOutcome.HIT));
    }
}
