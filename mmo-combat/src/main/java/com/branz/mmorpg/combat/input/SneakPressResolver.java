package com.branz.mmorpg.combat.input;

import java.util.Objects;

/** Resolves one Shift down-edge into dodge, crouch or no action using server ticks. */
public final class SneakPressResolver {
    public static final int DODGE_CANDIDATE_TICKS = 6;
    public static final int MOVEMENT_GRACE_TICKS = 3;
    public static final int CROUCH_HOLD_TICKS = 5;

    public SneakPressDecision resolve(
            SneakPressWindow window,
            long currentTick,
            boolean stillHeld,
            DirectionSnapshot direction) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(direction, "direction");
        if (currentTick < window.pressedTick()) {
            throw new IllegalArgumentException("currentTick must not precede Shift press");
        }
        long elapsed = currentTick - window.pressedTick();
        if (elapsed > DODGE_CANDIDATE_TICKS) {
            return SneakPressDecision.EXPIRED;
        }
        if (!stillHeld) {
            return SneakPressDecision.RELEASED;
        }
        if (direction != DirectionSnapshot.NEUTRAL && elapsed <= MOVEMENT_GRACE_TICKS) {
            return SneakPressDecision.DODGE;
        }
        if (elapsed >= CROUCH_HOLD_TICKS) {
            return SneakPressDecision.CROUCH;
        }
        return SneakPressDecision.WAITING;
    }
}
