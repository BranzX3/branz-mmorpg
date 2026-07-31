package com.branz.mmorpg.combat.guard;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.hitbox.CombatVector;
import java.util.Objects;

/** Deterministic directional weapon guard, perfect window and stability recovery. */
public final class GuardEngine {
    private final GuardProfile profile;

    public GuardEngine(GuardProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public GuardProfile profile() {
        return profile;
    }

    public Result<GuardRuntime, GuardErrorCode> start(GuardRuntime current, long tick) {
        current = tick(current, tick);
        if (current.brokenUntilTick() != GuardRuntime.NEVER) {
            return Result.failure(GuardErrorCode.GUARD_BROKEN, "Guard Break recovery is active.");
        }
        if (current.active()) {
            return Result.failure(GuardErrorCode.ALREADY_GUARDING, "Guard is already active.");
        }
        return Result.success(
                new GuardRuntime(
                        true,
                        tick,
                        current.stability(),
                        current.lastPressureTick(),
                        GuardRuntime.NEVER,
                        tick,
                        current.recoveryRemainder()));
    }

    public Result<GuardRuntime, GuardErrorCode> release(GuardRuntime current, long tick) {
        current = tick(current, tick);
        if (!current.active()) {
            return Result.failure(GuardErrorCode.NOT_GUARDING, "Guard is not active.");
        }
        return Result.success(
                new GuardRuntime(
                        false,
                        GuardRuntime.NEVER,
                        current.stability(),
                        current.lastPressureTick(),
                        current.brokenUntilTick(),
                        tick,
                        current.recoveryRemainder()));
    }

    public GuardPhase phaseAt(GuardRuntime runtime, long tick) {
        Objects.requireNonNull(runtime, "runtime");
        validateTick(runtime, tick);
        if (runtime.brokenUntilTick() != GuardRuntime.NEVER && tick < runtime.brokenUntilTick()) {
            return GuardPhase.BROKEN;
        }
        if (!runtime.active()) {
            return GuardPhase.INACTIVE;
        }
        return tick - runtime.startedTick() < profile.perfectWindowTicks()
                ? GuardPhase.PERFECT
                : GuardPhase.GUARDING;
    }

    public GuardResolution resolve(GuardRuntime current, long tick, GuardHitRequest request) {
        Objects.requireNonNull(request, "request");
        current = tick(current, tick);
        if (!request.guardable() || !current.active()) {
            return unchanged(GuardHitOutcome.UNGUARDED, current, request.incomingDamage());
        }
        if (!insideCone(request.defenderFacing(), request.directionToAttacker())) {
            return unchanged(GuardHitOutcome.OUTSIDE_CONE, current, request.incomingDamage());
        }

        boolean perfect =
                request.perfectGuardable() && phaseAt(current, tick) == GuardPhase.PERFECT;
        double pressure = request.guardPressure() * (perfect ? 0.5 : 1.0);
        int stamina = (int) Math.ceil(pressure);
        if (stamina > request.availableStamina()) {
            return unchanged(GuardHitOutcome.EXHAUSTED, current, request.incomingDamage());
        }
        double nextStability = Math.max(0, current.stability() - pressure);
        boolean broken = nextStability == 0;
        GuardRuntime next =
                new GuardRuntime(
                        !broken,
                        broken ? GuardRuntime.NEVER : current.startedTick(),
                        nextStability,
                        tick,
                        broken ? tick + profile.breakTicks() : GuardRuntime.NEVER,
                        tick,
                        0);
        GuardHitOutcome outcome =
                broken
                        ? GuardHitOutcome.GUARD_BREAK
                        : perfect ? GuardHitOutcome.PERFECT_GUARD : GuardHitOutcome.GUARDED;
        double finalDamage =
                perfect ? 0 : request.incomingDamage() * (1 - profile.physicalBlockRatio());
        return new GuardResolution(outcome, next, finalDamage, stamina, pressure);
    }

    public GuardRuntime tick(GuardRuntime current, long tick) {
        Objects.requireNonNull(current, "current");
        validateTick(current, tick);
        GuardRuntime base = current;
        if (base.brokenUntilTick() != GuardRuntime.NEVER) {
            if (tick < base.brokenUntilTick()) {
                return copyAt(base, tick, 0);
            }
            base =
                    new GuardRuntime(
                            false,
                            GuardRuntime.NEVER,
                            Math.max(base.stability(), profile.stabilityAfterBreak()),
                            base.lastPressureTick(),
                            GuardRuntime.NEVER,
                            base.lastTick(),
                            0);
        }
        if (base.stability() >= profile.maximumStability()
                || base.lastPressureTick() == GuardRuntime.NEVER
                || tick - base.lastPressureTick() < profile.recoveryDelayTicks()) {
            return copyAt(base, tick, base.recoveryRemainder());
        }
        long recoveryStart = base.lastPressureTick() + profile.recoveryDelayTicks() - 1L;
        long eligibleTicks = tick - Math.max(base.lastTick(), recoveryStart);
        if (eligibleTicks <= 0) {
            return copyAt(base, tick, base.recoveryRemainder());
        }
        double rate =
                base.active()
                        ? profile.activeRecoveryPerSecond()
                        : profile.inactiveRecoveryPerSecond();
        double recovery = base.recoveryRemainder() + eligibleTicks * rate / 20.0;
        double whole = Math.floor(recovery);
        double stability = Math.min(profile.maximumStability(), base.stability() + whole);
        double remainder = stability >= profile.maximumStability() ? 0 : recovery - whole;
        return new GuardRuntime(
                base.active(),
                base.startedTick(),
                stability,
                base.lastPressureTick(),
                GuardRuntime.NEVER,
                tick,
                remainder);
    }

    private boolean insideCone(CombatVector facing, CombatVector toAttacker) {
        CombatVector left = facing.normalizedHorizontal();
        CombatVector right = toAttacker.normalizedHorizontal();
        double dot = Math.max(-1, Math.min(1, left.x() * right.x() + left.z() * right.z()));
        return Math.toDegrees(Math.acos(dot)) <= profile.coneDegrees() / 2.0;
    }

    private static GuardResolution unchanged(
            GuardHitOutcome outcome, GuardRuntime runtime, double damage) {
        return new GuardResolution(outcome, runtime, damage, 0, 0);
    }

    private static GuardRuntime copyAt(GuardRuntime runtime, long tick, double remainder) {
        return new GuardRuntime(
                runtime.active(),
                runtime.startedTick(),
                runtime.stability(),
                runtime.lastPressureTick(),
                runtime.brokenUntilTick(),
                tick,
                remainder);
    }

    private static void validateTick(GuardRuntime runtime, long tick) {
        if (tick < runtime.lastTick()) {
            throw new IllegalArgumentException("tick must be monotonic");
        }
    }
}
