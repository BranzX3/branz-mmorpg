package com.branz.mmorpg.combat.input;

public enum DirectionSnapshot {
    FORWARD,
    BACK,
    LEFT,
    RIGHT,
    NEUTRAL;

    public static DirectionSnapshot fromAxes(double forward, double strafe) {
        if (!Double.isFinite(forward) || !Double.isFinite(strafe)) {
            throw new IllegalArgumentException("direction axes must be finite");
        }
        if (forward == 0.0 && strafe == 0.0) {
            return NEUTRAL;
        }
        if (Math.abs(forward) >= Math.abs(strafe)) {
            return forward >= 0.0 ? FORWARD : BACK;
        }
        return strafe >= 0.0 ? LEFT : RIGHT;
    }
}
