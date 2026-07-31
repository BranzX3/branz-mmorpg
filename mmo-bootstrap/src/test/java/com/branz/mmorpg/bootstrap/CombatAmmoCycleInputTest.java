package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.combat.input.DirectionSnapshot;
import org.junit.jupiter.api.Test;

class CombatAmmoCycleInputTest {
    @Test
    void onlyStationarySneakScrollWithRangedWeaponOwnsAmmoCycle() {
        assertTrue(AmmoCycleInputPolicy.ownsScroll(true, DirectionSnapshot.NEUTRAL, "BOW"));
        assertTrue(AmmoCycleInputPolicy.ownsScroll(true, DirectionSnapshot.NEUTRAL, "CROSSBOW"));
        assertFalse(AmmoCycleInputPolicy.ownsScroll(false, DirectionSnapshot.NEUTRAL, "BOW"));
        assertFalse(AmmoCycleInputPolicy.ownsScroll(true, DirectionSnapshot.FORWARD, "BOW"));
        assertFalse(AmmoCycleInputPolicy.ownsScroll(true, DirectionSnapshot.NEUTRAL, "SWORD"));
    }

    @Test
    void scrollDirectionHandlesBothHotbarWrapBoundaries() {
        assertEquals(1, AmmoCycleInputPolicy.scrollDirection(3, 4));
        assertEquals(-1, AmmoCycleInputPolicy.scrollDirection(4, 3));
        assertEquals(1, AmmoCycleInputPolicy.scrollDirection(8, 0));
        assertEquals(-1, AmmoCycleInputPolicy.scrollDirection(0, 8));
        assertThrows(
                IllegalArgumentException.class, () -> AmmoCycleInputPolicy.scrollDirection(2, 2));
    }
}
