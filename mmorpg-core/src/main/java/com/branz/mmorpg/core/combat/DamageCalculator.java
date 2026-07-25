package com.branz.mmorpg.core.combat;

import com.branz.mmorpg.api.combat.CombatPolicy;
import com.branz.mmorpg.api.combat.DamageType;
import com.branz.mmorpg.api.runtime.RandomSource;
import com.branz.mmorpg.api.stat.AttributeSnapshot;
import com.branz.mmorpg.api.stat.AttributeType;
import java.util.Objects;

/**
 * The damage formula, as a pure function.
 *
 * <pre>
 * base power
 *   -> offensive modifiers (Physical or Magic Power)
 *   -> critical
 *   -> mitigation
 *   -> minimum-damage floor
 * </pre>
 *
 * <p>Shield absorption and the health mutation happen in the engine, not here,
 * because those change state and this must stay replayable: same inputs and same
 * roll, same number, forever. That is what makes the golden tests meaningful.
 */
public final class DamageCalculator {

    private DamageCalculator() {
    }

    /**
     * Mitigation from a defensive stat.
     *
     * <pre>reduction = defense / (defense + K)</pre>
     *
     * <p>Hyperbolic rather than linear so defense never reaches immunity on its
     * own: every point is worth less than the last, and the result is capped
     * anyway. K is content-tier data, which is what makes the same curve usable
     * at level 1 and level 100.
     */
    public static double reduction(double defense, CombatPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (defense <= 0.0 || !Double.isFinite(defense)) {
            return 0.0;
        }
        double raw = defense / (defense + policy.mitigationConstant());
        return Math.min(policy.maximumReduction(), raw);
    }

    /** Attack power after the attacker's offensive attribute is applied. */
    public static double offensivePower(double basePower, DamageType type,
                                        AttributeSnapshot attacker) {
        if (attacker == null || !type.critical()) {
            // Environmental and true damage are flat: no attacker stat scales them.
            return basePower;
        }
        double power = type == DamageType.PHYSICAL
                ? attacker.get(AttributeType.PHYSICAL_POWER)
                : attacker.get(AttributeType.MAGIC_POWER);
        return basePower * (1.0 + power / 100.0);
    }

    /**
     * Rolls a critical hit.
     *
     * <p>The roll is taken from the injected source so a test can pin it, and it
     * is taken once per hit — never re-rolled to "check" a result.
     */
    public static boolean rollCritical(DamageType type, AttributeSnapshot attacker,
                                       RandomSource random) {
        if (attacker == null || !type.critical()) {
            return false;
        }
        Objects.requireNonNull(random, "random");
        return random.roll(attacker.get(AttributeType.CRITICAL_CHANCE));
    }

    public static double applyCritical(double power, boolean critical, AttributeSnapshot attacker) {
        if (!critical || attacker == null) {
            return power;
        }
        return power * attacker.get(AttributeType.CRITICAL_DAMAGE);
    }

    /**
     * Applies mitigation and the minimum-damage floor.
     *
     * <p>The floor exists so that an over-defended target still takes something:
     * a hit that rounds to zero makes a fight unwinnable in a way that reads as a
     * bug to players, and lets one build become mathematically immortal.
     */
    public static double mitigate(double power, DamageType type,
                                  AttributeSnapshot target, CombatPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (!type.mitigable() || target == null) {
            return power;
        }
        double defense = target.get(type.mitigatedBy());
        double mitigated = power * (1.0 - reduction(defense, policy));
        return Math.max(power * policy.minimumDamageFraction(), mitigated);
    }

    /** Multiplier applied when both sides are player-controlled. */
    public static double pvpScale(boolean attackerIsPlayer, boolean targetIsPlayer,
                                  CombatPolicy policy) {
        return attackerIsPlayer && targetIsPlayer ? policy.pvpCoefficient() : 1.0;
    }
}
