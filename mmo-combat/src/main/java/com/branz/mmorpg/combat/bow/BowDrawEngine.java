package com.branz.mmorpg.combat.bow;

import java.util.Objects;

/** Deterministic press/hold/release Bow continuum on authoritative server ticks. */
public final class BowDrawEngine {
    private final BowDrawProfile profile;

    public BowDrawEngine(BowDrawProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public BowDrawProfile profile() {
        return profile;
    }

    public BowDrawRuntime start(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("bow start tick must not be negative");
        }
        return new BowDrawRuntime(tick, tick, BowDrawPhase.DRAWING, 0);
    }

    public BowTickResolution tick(BowDrawRuntime current, long tick, int availableStamina) {
        Objects.requireNonNull(current, "current");
        validateTickAndStamina(current, tick, availableStamina);
        if (current.phase().terminal()) {
            throw new IllegalStateException("terminal bow draw cannot advance");
        }
        BowDrawPhase nextPhase = phaseAt(tick - current.startTick());
        if (nextPhase != BowDrawPhase.STRAINED) {
            return new BowTickResolution(
                    new BowDrawRuntime(current.startTick(), tick, nextPhase, 0), 0, false);
        }
        if (availableStamina == 0) {
            return cancelled(current, tick, 0);
        }
        long strainStart =
                current.startTick() + profile.fullDrawTicks() + profile.freeFullDrawHoldTicks();
        long chargedTicks = tick - Math.max(current.lastTick(), strainStart - 1);
        double accumulated =
                current.strainDrainRemainder()
                        + chargedTicks * profile.strainStaminaPerSecond() / 20.0;
        int requested = (int) Math.floor(accumulated + 1.0e-12);
        int spent = Math.min(requested, availableStamina);
        if (requested > availableStamina || availableStamina - spent == 0) {
            return cancelled(current, tick, spent);
        }
        return new BowTickResolution(
                new BowDrawRuntime(
                        current.startTick(), tick, nextPhase, Math.max(0, accumulated - requested)),
                spent,
                false);
    }

    public BowReleaseResolution release(BowDrawRuntime current, long tick, int availableStamina) {
        BowTickResolution advanced = tick(current, tick, availableStamina);
        if (advanced.loweredForExhaustion()) {
            return new BowReleaseResolution(
                    advanced.runtime(),
                    BowReleaseOutcome.EXHAUSTED,
                    advanced.staminaSpent(),
                    java.util.Optional.empty());
        }
        long elapsed = tick - current.startTick();
        if (elapsed < profile.minimumDrawTicks()) {
            BowDrawRuntime cancelled =
                    new BowDrawRuntime(
                            current.startTick(),
                            tick,
                            BowDrawPhase.CANCELLED,
                            advanced.runtime().strainDrainRemainder());
            return new BowReleaseResolution(
                    cancelled,
                    BowReleaseOutcome.TOO_EARLY,
                    advanced.staminaSpent(),
                    java.util.Optional.empty());
        }
        double ratio =
                Math.min(
                        1,
                        (double) (elapsed - profile.minimumDrawTicks())
                                / (profile.fullDrawTicks() - profile.minimumDrawTicks()));
        BowShotCharge shot =
                new BowShotCharge(
                        ratio,
                        lerp(profile.minimumVelocityMultiplier(), 1, ratio),
                        lerp(profile.minimumPostureMultiplier(), 1, ratio),
                        profile.maximumPenetrationPercentage() * ratio);
        BowDrawRuntime released =
                new BowDrawRuntime(
                        current.startTick(),
                        tick,
                        BowDrawPhase.RELEASED,
                        advanced.runtime().strainDrainRemainder());
        return new BowReleaseResolution(
                released,
                BowReleaseOutcome.FIRED,
                advanced.staminaSpent(),
                java.util.Optional.of(shot));
    }

    public BowDrawRuntime cancel(BowDrawRuntime current, long tick) {
        Objects.requireNonNull(current, "current");
        validateTickAndStamina(current, tick, 0);
        if (current.phase().terminal()) {
            return current;
        }
        return new BowDrawRuntime(
                current.startTick(), tick, BowDrawPhase.CANCELLED, current.strainDrainRemainder());
    }

    private BowTickResolution cancelled(BowDrawRuntime current, long tick, int spent) {
        return new BowTickResolution(
                new BowDrawRuntime(current.startTick(), tick, BowDrawPhase.CANCELLED, 0),
                spent,
                true);
    }

    private BowDrawPhase phaseAt(long elapsed) {
        if (elapsed < profile.minimumDrawTicks()) {
            return BowDrawPhase.DRAWING;
        }
        if (elapsed < profile.fullDrawTicks()) {
            return BowDrawPhase.READY_DRAW;
        }
        if (elapsed < profile.fullDrawTicks() + profile.freeFullDrawHoldTicks()) {
            return BowDrawPhase.FULL_DRAW;
        }
        return BowDrawPhase.STRAINED;
    }

    private static void validateTickAndStamina(
            BowDrawRuntime current, long tick, int availableStamina) {
        if (tick < current.lastTick() || availableStamina < 0) {
            throw new IllegalArgumentException("bow tick/stamina is invalid");
        }
    }

    private static double lerp(double minimum, double maximum, double ratio) {
        return minimum + (maximum - minimum) * ratio;
    }
}
