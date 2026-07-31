package com.branz.mmorpg.combat.poise;

import com.branz.mmorpg.combat.cc.CcSeverity;
import java.util.Objects;
import java.util.Optional;

/** Hidden short-window player poise accumulation and deterministic decay. */
public final class PoiseEngine {
    private final PoiseProfile profile;

    public PoiseEngine(PoiseProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public PoiseProfile profile() {
        return profile;
    }

    public PoiseResolution apply(
            PoiseRuntime current,
            long tick,
            double poiseDamage,
            double hyperArmorMultiplier,
            CcSeverity severity) {
        Objects.requireNonNull(severity, "severity");
        if (!Double.isFinite(poiseDamage)
                || poiseDamage < 0
                || !Double.isFinite(hyperArmorMultiplier)
                || hyperArmorMultiplier < 0
                || hyperArmorMultiplier > 1) {
            throw new IllegalArgumentException("invalid poise impact");
        }
        current = tick(current, tick);
        double accumulated = current.accumulated() + poiseDamage * hyperArmorMultiplier;
        if (accumulated >= profile.threshold()) {
            return new PoiseResolution(
                    new PoiseRuntime(0, tick, tick, 0), false, Optional.of(severity));
        }
        return new PoiseResolution(
                new PoiseRuntime(accumulated, tick, tick, 0), true, Optional.empty());
    }

    public PoiseRuntime tick(PoiseRuntime current, long tick) {
        Objects.requireNonNull(current, "current");
        if (tick < current.lastTick()) {
            throw new IllegalArgumentException("tick must be monotonic");
        }
        if (current.accumulated() == 0
                || current.lastDamageTick() == PoiseRuntime.NEVER
                || tick - current.lastDamageTick() < profile.accumulationWindowTicks()) {
            return copyAt(current, tick, current.decayRemainder());
        }
        long decayStart = current.lastDamageTick() + profile.accumulationWindowTicks() - 1L;
        long eligibleTicks = tick - Math.max(current.lastTick(), decayStart);
        if (eligibleTicks <= 0) {
            return copyAt(current, tick, current.decayRemainder());
        }
        double decay =
                current.decayRemainder()
                        + eligibleTicks
                                * profile.threshold()
                                * profile.decayRatioPerSecond()
                                / 20.0;
        double whole = Math.floor(decay);
        double accumulated = Math.max(0, current.accumulated() - whole);
        double remainder = accumulated == 0 ? 0 : decay - whole;
        return new PoiseRuntime(accumulated, current.lastDamageTick(), tick, remainder);
    }

    private static PoiseRuntime copyAt(PoiseRuntime runtime, long tick, double remainder) {
        return new PoiseRuntime(runtime.accumulated(), runtime.lastDamageTick(), tick, remainder);
    }
}
