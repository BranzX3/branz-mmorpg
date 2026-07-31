package com.branz.mmorpg.combat.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SneakPressResolverTest {
    private final SneakPressResolver resolver = new SneakPressResolver();
    private final SneakPressWindow press = new SneakPressWindow(100);

    @Test
    void movementAtPressOrWithinThreeTicksDodges() {
        assertEquals(
                SneakPressDecision.DODGE,
                resolver.resolve(press, 100, true, DirectionSnapshot.FORWARD));
        assertEquals(
                SneakPressDecision.DODGE,
                resolver.resolve(press, 103, true, DirectionSnapshot.LEFT));
        assertEquals(
                SneakPressDecision.WAITING,
                resolver.resolve(press, 104, true, DirectionSnapshot.RIGHT));
    }

    @Test
    void stationaryHoldCrouchesAndEarlyReleaseDoesNothing() {
        assertEquals(
                SneakPressDecision.WAITING,
                resolver.resolve(press, 104, true, DirectionSnapshot.NEUTRAL));
        assertEquals(
                SneakPressDecision.CROUCH,
                resolver.resolve(press, 105, true, DirectionSnapshot.NEUTRAL));
        assertEquals(
                SneakPressDecision.RELEASED,
                resolver.resolve(press, 102, false, DirectionSnapshot.NEUTRAL));
        assertEquals(
                SneakPressDecision.EXPIRED,
                resolver.resolve(press, 107, true, DirectionSnapshot.NEUTRAL));
    }
}
