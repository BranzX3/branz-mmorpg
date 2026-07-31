package com.branz.mmorpg.combat.input;

public record SneakPressWindow(long pressedTick) {
    public SneakPressWindow {
        if (pressedTick < 0) {
            throw new IllegalArgumentException("pressedTick must not be negative");
        }
    }
}
