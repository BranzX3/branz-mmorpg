package com.branz.mmorpg.core.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.stat.AttributeType;
import org.junit.jupiter.api.Test;

class ResourcePoolTest {

    @Test
    void loweringTheMaximumClampsCurrentAndDoesNotPreserveTheRatio() {
        ResourcePool health = new ResourcePool(AttributeType.MAX_HEALTH, 200.0);
        health.add(-50.0);
        assertEquals(150.0, health.current(), 1e-9);

        health.maximum(100.0);

        assertEquals(100.0, health.current(), 1e-9);

        // Restoring the maximum must not hand the lost health back.
        health.maximum(200.0);
        assertEquals(100.0, health.current(), 1e-9);
    }

    @Test
    void equipUnequipCyclesAreNotAFreeHeal() {
        ResourcePool health = new ResourcePool(AttributeType.MAX_HEALTH, 200.0);
        health.add(-190.0);
        assertEquals(10.0, health.current(), 1e-9);

        for (int i = 0; i < 10; i++) {
            health.maximum(100.0);
            health.maximum(200.0);
        }

        assertEquals(10.0, health.current(), 1e-9);
    }

    @Test
    void spendIsAllOrNothing() {
        ResourcePool mana = new ResourcePool(AttributeType.MAX_MANA, 50.0);

        assertTrue(mana.spend(30.0));
        assertEquals(20.0, mana.current(), 1e-9);

        assertFalse(mana.spend(21.0), "an unaffordable cost is refused");
        assertEquals(20.0, mana.current(), 1e-9, "and nothing is deducted");
    }

    @Test
    void valuesClampToZeroAndMaximum() {
        ResourcePool stamina = new ResourcePool(AttributeType.MAX_STAMINA, 100.0);

        stamina.add(-500.0);
        assertEquals(0.0, stamina.current(), 1e-9);
        assertTrue(stamina.depleted());

        stamina.add(500.0);
        assertEquals(100.0, stamina.current(), 1e-9);
        assertEquals(1.0, stamina.ratio(), 1e-9);
    }

    @Test
    void regenerationScalesWithElapsedTicks() {
        ResourcePool mana = new ResourcePool(AttributeType.MAX_MANA, 100.0);
        mana.add(-100.0);

        mana.regenerate(5.0, 20L, false, 0.25);
        assertEquals(5.0, mana.current(), 1e-9, "20 ticks is one second at 5/s");

        mana.regenerate(5.0, 40L, false, 0.25);
        assertEquals(15.0, mana.current(), 1e-9, "twice the ticks regenerates twice as much");
    }

    @Test
    void combatReducesRegenerationByTheConfiguredFactor() {
        ResourcePool mana = new ResourcePool(AttributeType.MAX_MANA, 100.0);
        mana.add(-100.0);

        mana.regenerate(10.0, 20L, true, 0.25);

        assertEquals(2.5, mana.current(), 1e-9);
    }

    @Test
    void regenerationIgnoresNonPositiveInput() {
        ResourcePool mana = new ResourcePool(AttributeType.MAX_MANA, 100.0);
        mana.add(-50.0);

        assertEquals(50.0, mana.regenerate(5.0, 0L, false, 1.0), 1e-9);
        assertEquals(50.0, mana.regenerate(0.0, 100L, false, 1.0), 1e-9);
        assertEquals(50.0, mana.regenerate(5.0, -20L, false, 1.0), 1e-9);
    }

    @Test
    void rejectsInvalidNumbersAndNonResourceAttributes() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourcePool(AttributeType.PHYSICAL_POWER, 10.0));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourcePool(AttributeType.MAX_HEALTH, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourcePool(AttributeType.MAX_HEALTH, -1.0));

        ResourcePool health = new ResourcePool(AttributeType.MAX_HEALTH, 100.0);
        assertThrows(IllegalArgumentException.class, () -> health.add(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> health.spend(-1.0));
        assertThrows(IllegalArgumentException.class,
                () -> health.maximum(Double.POSITIVE_INFINITY));
    }

    @Test
    void supportsEmptyRageAndFullFastEnergyPolicies() {
        ResourcePool rage = new ResourcePool(
                com.branz.mmorpg.api.skill.ResourceType.RAGE, 100.0, 0.0);
        ResourcePool energy = new ResourcePool(
                com.branz.mmorpg.api.skill.ResourceType.ENERGY, 100.0, 100.0);

        assertEquals(0.0, rage.current(), 1e-9);
        assertEquals(100.0, energy.current(), 1e-9);
        energy.spend(24.0);
        var policy = com.branz.mmorpg.api.stat.ResourcePolicy.standard(
                com.branz.mmorpg.api.skill.ResourceType.ENERGY);
        energy.regenerate(policy.regenerationPerSecond(), 20, true,
                policy.combatRegenerationFactor());
        assertEquals(88.0, energy.current(), 1e-9);
    }
}
