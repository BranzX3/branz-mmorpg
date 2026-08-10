package com.branz.mmorpg.combat.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldHealthResolverTest {
    private final WorldHealthResolver resolver = new WorldHealthResolver();

    @Test
    void ordinaryDamageUsesTheEntitysCurrentAndMaximumHealth() {
        WorldHealthResolution result = resolver.damage(10, 10, 3.5);

        assertEquals(10, result.previous());
        assertEquals(6.5, result.current());
        assertEquals(10, result.maximum());
        assertEquals(3.5, result.appliedAmount());
        assertFalse(result.lethalNow());
    }

    @Test
    void zeroDamagePreservesCurrentHealth() {
        WorldHealthResolution result = resolver.damage(7.5, 10, 0);

        assertEquals(7.5, result.current());
        assertEquals(0, result.appliedAmount());
        assertFalse(result.lethalNow());
    }

    @Test
    void lethalDamageClampsAtTheRemainingHealth() {
        WorldHealthResolution result = resolver.damage(4, 10, 1000);

        assertEquals(4, result.appliedAmount());
        assertEquals(0, result.current());
        assertTrue(result.lethalNow());
    }

    @Test
    void invalidHealthOrDamageFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> resolver.damage(11, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> resolver.damage(10, 10, -1));
    }
}
