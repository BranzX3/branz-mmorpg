package com.branz.mmorpg.api.combat;

/**
 * Server-wide combat rules.
 *
 * @param pvpEnabled            whether player-versus-player damage is permitted at all
 * @param friendlyFire          whether allies can damage each other
 * @param pvpCoefficient        multiplier applied to player-versus-player damage
 * @param mitigationConstant    the K in {@code reduction = defense / (defense + K)}
 * @param maximumReduction      cap on mitigation, so defense can never reach immunity
 * @param minimumDamageFraction floor on final damage as a fraction of the incoming
 *                              amount, so a hit always means something
 * @param combatTimeoutMillis   inactivity before a combatant leaves combat
 */
public record CombatPolicy(
        boolean pvpEnabled,
        boolean friendlyFire,
        double pvpCoefficient,
        double mitigationConstant,
        double maximumReduction,
        double minimumDamageFraction,
        long combatTimeoutMillis) {

    public CombatPolicy {
        if (mitigationConstant <= 0.0 || !Double.isFinite(mitigationConstant)) {
            throw new IllegalArgumentException("mitigationConstant must be positive and finite");
        }
        if (maximumReduction < 0.0 || maximumReduction >= 1.0) {
            // A reduction of 1.0 is immortality, which is never a balance value.
            throw new IllegalArgumentException("maximumReduction must be in [0, 1)");
        }
        if (minimumDamageFraction < 0.0 || minimumDamageFraction > 1.0) {
            throw new IllegalArgumentException("minimumDamageFraction must be in [0, 1]");
        }
        if (pvpCoefficient < 0.0 || !Double.isFinite(pvpCoefficient)) {
            throw new IllegalArgumentException("pvpCoefficient must be non-negative and finite");
        }
        if (combatTimeoutMillis < 0) {
            throw new IllegalArgumentException("combatTimeoutMillis must not be negative");
        }
    }

    /**
     * Launch defaults: PvE only, no friendly fire, 85% mitigation cap, and a 10%
     * damage floor.
     */
    public static CombatPolicy defaults() {
        return new CombatPolicy(false, false, 0.5, 100.0, 0.85, 0.10, 6_000L);
    }

    public CombatPolicy withPvp(boolean enabled) {
        return new CombatPolicy(enabled, friendlyFire, pvpCoefficient, mitigationConstant,
                maximumReduction, minimumDamageFraction, combatTimeoutMillis);
    }

    public CombatPolicy withFriendlyFire(boolean enabled) {
        return new CombatPolicy(pvpEnabled, enabled, pvpCoefficient, mitigationConstant,
                maximumReduction, minimumDamageFraction, combatTimeoutMillis);
    }
}
