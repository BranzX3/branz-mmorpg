package com.branz.mmorpg.combat.dodge;

import java.util.Objects;

/** Canonical V1 load-tier dodge values. */
public record DodgeProfile(
        DodgeLoad load,
        int staminaCost,
        int iframeTicks,
        int totalTicks,
        double travelDistance,
        int movementTicks) {
    public DodgeProfile {
        Objects.requireNonNull(load, "load");
        if (staminaCost < 0
                || iframeTicks < 0
                || totalTicks < 1
                || iframeTicks + 1 > totalTicks
                || !Double.isFinite(travelDistance)
                || travelDistance < 0
                || movementTicks < 1
                || movementTicks > totalTicks) {
            throw new IllegalArgumentException("invalid dodge profile");
        }
    }

    public static DodgeProfile canonical(DodgeLoad load) {
        return switch (Objects.requireNonNull(load, "load")) {
            case LIGHT -> new DodgeProfile(load, 25, 6, 14, 4.2, 4);
            case MEDIUM -> new DodgeProfile(load, 30, 4, 16, 3.5, 4);
            case HEAVY -> new DodgeProfile(load, 35, 2, 18, 2.6, 4);
            case OVERLOADED -> new DodgeProfile(load, 40, 0, 20, 1.4, 4);
        };
    }
}
