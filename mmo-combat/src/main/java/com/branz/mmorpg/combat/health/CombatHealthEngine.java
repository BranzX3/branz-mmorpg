package com.branz.mmorpg.combat.health;

import java.util.Objects;

/** Deterministic MMO-scale damage, healing, death, respawn and open-world recovery. */
public final class CombatHealthEngine {
    private final CombatHealthProfile profile;

    public CombatHealthEngine(CombatHealthProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public CombatHealthProfile profile() {
        return profile;
    }

    public CombatHealthResolution damage(CombatHealthRuntime current, long tick, double amount) {
        validateAmount(amount);
        current = advance(current, tick);
        if (current.dead() || amount == 0) {
            return new CombatHealthResolution(current, 0, false);
        }
        double applied = Math.min(current.current(), amount);
        double remaining = current.current() - applied;
        return new CombatHealthResolution(
                new CombatHealthRuntime(remaining, tick, tick), applied, remaining == 0);
    }

    public CombatHealthResolution heal(CombatHealthRuntime current, long tick, double amount) {
        validateAmount(amount);
        current = advance(current, tick);
        if (current.dead() || amount == 0 || current.current() >= profile.maximum()) {
            return new CombatHealthResolution(current, 0, false);
        }
        double healed = Math.min(profile.maximum() - current.current(), amount);
        return new CombatHealthResolution(
                new CombatHealthRuntime(current.current() + healed, current.lastDamageTick(), tick),
                healed,
                false);
    }

    public CombatHealthRuntime tickOpenWorld(
            CombatHealthRuntime current, long tick, boolean outOfCombat) {
        Objects.requireNonNull(current, "current");
        validateTick(current, tick);
        if (current.current() > profile.maximum()) {
            throw new IllegalArgumentException("combat health exceeds profile maximum");
        }
        double cap = profile.maximum() * profile.openWorldRecoveryCapRatio();
        if (!outOfCombat
                || current.dead()
                || current.current() >= cap
                || current.lastDamageTick() == CombatHealthRuntime.NEVER
                || tick - current.lastDamageTick() < profile.openWorldRecoveryDelayTicks()) {
            return advance(current, tick);
        }
        long recoveryStart = current.lastDamageTick() + profile.openWorldRecoveryDelayTicks() - 1L;
        long eligibleTicks = tick - Math.max(current.lastTick(), recoveryStart);
        double recovered =
                eligibleTicks
                        * profile.maximum()
                        * profile.openWorldRecoveryRatioPerSecond()
                        / 20.0;
        return new CombatHealthRuntime(
                Math.min(cap, current.current() + recovered), current.lastDamageTick(), tick);
    }

    public CombatHealthRuntime kill(CombatHealthRuntime current, long tick) {
        current = advance(current, tick);
        return current.dead() ? current : new CombatHealthRuntime(0, tick, tick);
    }

    public CombatHealthRuntime respawn(CombatHealthRuntime current, long tick) {
        current = advance(current, tick);
        if (!current.dead()) {
            throw new IllegalStateException("only a dead combatant may respawn");
        }
        return new CombatHealthRuntime(
                profile.maximum() * profile.respawnRatio(), CombatHealthRuntime.NEVER, tick);
    }

    private CombatHealthRuntime advance(CombatHealthRuntime current, long tick) {
        Objects.requireNonNull(current, "current");
        validateTick(current, tick);
        if (current.current() > profile.maximum()) {
            throw new IllegalArgumentException("combat health exceeds profile maximum");
        }
        return new CombatHealthRuntime(current.current(), current.lastDamageTick(), tick);
    }

    private static void validateTick(CombatHealthRuntime current, long tick) {
        if (tick < current.lastTick()) {
            throw new IllegalArgumentException("tick must be monotonic");
        }
    }

    private static void validateAmount(double amount) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("health amount must be finite and non-negative");
        }
    }
}
