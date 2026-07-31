package com.branz.mmorpg.combat.posture;

import java.util.Objects;

/** Deterministic normal-enemy posture damage, break and delayed regeneration. */
public final class PostureEngine {
    private final PostureProfile profile;

    public PostureEngine(PostureProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public PostureProfile profile() {
        return profile;
    }

    public PostureResolution damage(PostureRuntime current, long tick, double amount) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("posture damage must be finite and non-negative");
        }
        current = tick(current, tick);
        if (phaseAt(current, tick) == PosturePhase.BROKEN || amount == 0) {
            return new PostureResolution(current, phaseAt(current, tick), false);
        }
        double remaining = Math.max(0, current.current() - amount);
        boolean broke = remaining == 0;
        PostureRuntime next =
                new PostureRuntime(
                        remaining,
                        tick,
                        broke ? tick + profile.breakTicks() : PostureRuntime.NEVER,
                        tick,
                        0);
        return new PostureResolution(
                next, broke ? PosturePhase.BROKEN : PosturePhase.STABLE, broke);
    }

    public PostureRuntime tick(PostureRuntime current, long tick) {
        Objects.requireNonNull(current, "current");
        validateTick(current, tick);
        if (current.brokenUntilTick() != PostureRuntime.NEVER) {
            if (tick < current.brokenUntilTick()) {
                return copyAt(current, tick, 0);
            }
            return new PostureRuntime(
                    profile.maximum(), current.lastDamageTick(), PostureRuntime.NEVER, tick, 0);
        }
        if (current.current() >= profile.maximum()
                || current.lastDamageTick() == PostureRuntime.NEVER
                || tick - current.lastDamageTick() < profile.recoveryDelayTicks()) {
            return copyAt(current, tick, current.recoveryRemainder());
        }
        long recoveryStart = current.lastDamageTick() + profile.recoveryDelayTicks() - 1L;
        long eligibleTicks = tick - Math.max(current.lastTick(), recoveryStart);
        if (eligibleTicks <= 0) {
            return copyAt(current, tick, current.recoveryRemainder());
        }
        double recovery =
                current.recoveryRemainder() + eligibleTicks * profile.recoveryPerSecond() / 20.0;
        double whole = Math.floor(recovery);
        double posture = Math.min(profile.maximum(), current.current() + whole);
        double remainder = posture >= profile.maximum() ? 0 : recovery - whole;
        return new PostureRuntime(
                posture, current.lastDamageTick(), PostureRuntime.NEVER, tick, remainder);
    }

    public PosturePhase phaseAt(PostureRuntime runtime, long tick) {
        Objects.requireNonNull(runtime, "runtime");
        validateTick(runtime, tick);
        return runtime.brokenUntilTick() != PostureRuntime.NEVER && tick < runtime.brokenUntilTick()
                ? PosturePhase.BROKEN
                : PosturePhase.STABLE;
    }

    private static PostureRuntime copyAt(PostureRuntime runtime, long tick, double remainder) {
        return new PostureRuntime(
                runtime.current(),
                runtime.lastDamageTick(),
                runtime.brokenUntilTick(),
                tick,
                remainder);
    }

    private static void validateTick(PostureRuntime runtime, long tick) {
        if (tick < runtime.lastTick()) {
            throw new IllegalArgumentException("tick must be monotonic");
        }
    }
}
