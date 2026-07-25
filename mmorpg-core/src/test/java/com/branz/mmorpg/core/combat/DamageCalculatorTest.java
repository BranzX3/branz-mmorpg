package com.branz.mmorpg.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.combat.CombatPolicy;
import com.branz.mmorpg.api.combat.DamageType;
import com.branz.mmorpg.api.stat.AttributeSnapshot;
import com.branz.mmorpg.api.stat.AttributeType;
import com.branz.mmorpg.core.fixture.ScriptedRandomSource;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Golden tests for the damage formula. These numbers are the contract. */
class DamageCalculatorTest {

    private static final CombatPolicy POLICY = CombatPolicy.defaults();

    @Test
    void mitigationCurveIsHyperbolicAndCapped() {
        // reduction = defense / (defense + 100)
        assertEquals(0.000, DamageCalculator.reduction(0, POLICY), 1e-9);
        assertEquals(0.500, DamageCalculator.reduction(100, POLICY), 1e-9);
        assertEquals(0.750, DamageCalculator.reduction(300, POLICY), 1e-9);
        assertEquals(0.850, DamageCalculator.reduction(1_000_000, POLICY), 1e-9,
                "defense alone can never reach immunity");
    }

    @Test
    void mitigationHasDiminishingReturns() {
        double firstHundred = DamageCalculator.reduction(100, POLICY)
                - DamageCalculator.reduction(0, POLICY);
        double secondHundred = DamageCalculator.reduction(200, POLICY)
                - DamageCalculator.reduction(100, POLICY);

        assertTrue(secondHundred < firstHundred, "each point of defense is worth less than the last");
    }

    @Test
    void negativeOrInvalidDefenceGivesNoReduction() {
        assertEquals(0.0, DamageCalculator.reduction(-50, POLICY), 1e-9);
        assertEquals(0.0, DamageCalculator.reduction(Double.NaN, POLICY), 1e-9);
    }

    @Test
    void offensivePowerScalesWithTheMatchingAttribute() {
        AttributeSnapshot attacker = snapshot(Map.of(
                AttributeType.PHYSICAL_POWER, 50.0,
                AttributeType.MAGIC_POWER, 200.0));

        assertEquals(150.0,
                DamageCalculator.offensivePower(100.0, DamageType.PHYSICAL, attacker), 1e-9);
        assertEquals(300.0,
                DamageCalculator.offensivePower(100.0, DamageType.MAGIC, attacker), 1e-9);
    }

    @Test
    void trueAndEnvironmentalDamageIgnoreAttackerStatsAndMitigation() {
        AttributeSnapshot attacker = snapshot(Map.of(AttributeType.PHYSICAL_POWER, 500.0));
        AttributeSnapshot tank = snapshot(Map.of(AttributeType.DEFENSE, 900.0));

        assertEquals(100.0, DamageCalculator.offensivePower(100.0, DamageType.TRUE, attacker), 1e-9);
        assertEquals(100.0, DamageCalculator.mitigate(100.0, DamageType.TRUE, tank, POLICY), 1e-9);
        assertEquals(100.0,
                DamageCalculator.mitigate(100.0, DamageType.ENVIRONMENTAL, tank, POLICY), 1e-9);
    }

    @Test
    void mitigationAppliesTheMinimumDamageFloor() {
        AttributeSnapshot tank = snapshot(Map.of(AttributeType.DEFENSE, 10_000.0));

        // 85% cap would leave 15, but the 10% floor is lower, so the cap governs
        assertEquals(15.0, DamageCalculator.mitigate(100.0, DamageType.PHYSICAL, tank, POLICY), 1e-9);

        CombatPolicy harsh = new CombatPolicy(false, false, 0.5, 100.0, 0.99, 0.10, 6_000L);
        assertEquals(10.0, DamageCalculator.mitigate(100.0, DamageType.PHYSICAL, tank, harsh), 1e-9,
                "the floor guarantees a hit always means something");
    }

    @Test
    void criticalUsesTheInjectedRollAndTheCriticalDamageAttribute() {
        AttributeSnapshot attacker = snapshot(Map.of(
                AttributeType.CRITICAL_CHANCE, 0.30,
                AttributeType.CRITICAL_DAMAGE, 2.0));

        assertTrue(DamageCalculator.rollCritical(DamageType.PHYSICAL, attacker,
                ScriptedRandomSource.of(0.10)));
        assertFalse(DamageCalculator.rollCritical(DamageType.PHYSICAL, attacker,
                ScriptedRandomSource.of(0.50)));
        assertEquals(200.0, DamageCalculator.applyCritical(100.0, true, attacker), 1e-9);
        assertEquals(100.0, DamageCalculator.applyCritical(100.0, false, attacker), 1e-9);
    }

    @Test
    void trueDamageCannotCrit() {
        AttributeSnapshot attacker = snapshot(Map.of(AttributeType.CRITICAL_CHANCE, 0.60));

        assertFalse(DamageCalculator.rollCritical(DamageType.TRUE, attacker,
                ScriptedRandomSource.of(0.0)));
        assertFalse(DamageCalculator.rollCritical(DamageType.ENVIRONMENTAL, attacker,
                ScriptedRandomSource.of(0.0)));
    }

    @Test
    void pvpCoefficientAppliesOnlyBetweenPlayers() {
        assertEquals(0.5, DamageCalculator.pvpScale(true, true, POLICY), 1e-9);
        assertEquals(1.0, DamageCalculator.pvpScale(true, false, POLICY), 1e-9);
        assertEquals(1.0, DamageCalculator.pvpScale(false, true, POLICY), 1e-9);
    }

    @Test
    void goldenEndToEndFigure() {
        AttributeSnapshot attacker = snapshot(Map.of(
                AttributeType.PHYSICAL_POWER, 100.0,
                AttributeType.CRITICAL_DAMAGE, 2.0));
        AttributeSnapshot target = snapshot(Map.of(AttributeType.DEFENSE, 100.0));

        double raw = DamageCalculator.offensivePower(50.0, DamageType.PHYSICAL, attacker);
        double crit = DamageCalculator.applyCritical(raw, true, attacker);
        double finalDamage = DamageCalculator.mitigate(crit, DamageType.PHYSICAL, target, POLICY);

        // 50 * (1 + 100/100) = 100; crit x2 = 200; 50% mitigation = 100
        assertEquals(100.0, raw, 1e-9);
        assertEquals(200.0, crit, 1e-9);
        assertEquals(100.0, finalDamage, 1e-9);
    }

    @Test
    void policyRejectsImmortalityAndNonsense() {
        assertTrue(assertThrowsIllegal(() -> new CombatPolicy(false, false, 0.5, 100.0, 1.0, 0.1, 0)));
        assertTrue(assertThrowsIllegal(() -> new CombatPolicy(false, false, 0.5, 0.0, 0.85, 0.1, 0)));
        assertTrue(assertThrowsIllegal(() -> new CombatPolicy(false, false, -1.0, 100.0, 0.85, 0.1, 0)));
        assertTrue(assertThrowsIllegal(() -> new CombatPolicy(false, false, 0.5, 100.0, 0.85, 1.5, 0)));
    }

    private static boolean assertThrowsIllegal(Runnable work) {
        try {
            work.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static AttributeSnapshot snapshot(Map<AttributeType, Double> overrides) {
        EnumMap<AttributeType, Double> values = new EnumMap<>(AttributeType.class);
        for (AttributeType attribute : AttributeType.values()) {
            values.put(attribute, attribute.defaultValue());
        }
        values.putAll(overrides);
        return new AttributeSnapshot(values);
    }
}
