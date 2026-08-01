package com.branz.mmorpg.social.pvp;

/** Explicit duel/arena balance and consumable policy. */
public record PvpCombatProfile(
        double damageMultiplier,
        double healingMultiplier,
        double guardPressureMultiplier,
        double ccDurationMultiplier,
        int hardCcImmunityTicks,
        boolean flaskAllowed,
        boolean externalBuffsAllowed) {
    public PvpCombatProfile {
        requireMultiplier(damageMultiplier, "damageMultiplier");
        requireMultiplier(healingMultiplier, "healingMultiplier");
        requireMultiplier(guardPressureMultiplier, "guardPressureMultiplier");
        requireMultiplier(ccDurationMultiplier, "ccDurationMultiplier");
        if (hardCcImmunityTicks < 1) {
            throw new IllegalArgumentException("hardCcImmunityTicks must be positive");
        }
    }

    public static PvpCombatProfile canonical() {
        return new PvpCombatProfile(0.70, 0.60, 0.85, 0.65, 30, true, false);
    }

    public boolean durabilityLossAllowed() {
        return false;
    }

    public boolean deathPouchAllowed() {
        return false;
    }

    private static void requireMultiplier(double value, String name) {
        if (!Double.isFinite(value) || value <= 0 || value > 2) {
            throw new IllegalArgumentException(name + " must be finite and in (0, 2]");
        }
    }
}
