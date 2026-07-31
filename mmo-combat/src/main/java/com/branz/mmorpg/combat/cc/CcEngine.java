package com.branz.mmorpg.combat.cc;

import java.util.Objects;
import java.util.Optional;

/** Deterministic physical CC hierarchy, PvE immunity and PvP diminishing returns. */
public final class CcEngine {
    public static final int PVE_HARD_CC_IMMUNITY_TICKS = 24;
    public static final int PVP_HARD_CC_IMMUNITY_TICKS = 30;
    public static final int PVP_REPEAT_WINDOW_TICKS = 160;
    public static final double PVP_DURATION_MULTIPLIER = 0.60;

    public CcApplication apply(CcRuntime current, long tick, CcRequest request) {
        Objects.requireNonNull(request, "request");
        current = tick(current, tick);

        if (current.active().isPresent()) {
            CcSeverity active = current.active().orElseThrow();
            if (!request.severity().strongerThan(active) && !request.comboContinuation()) {
                return rejected(CcApplicationOutcome.REJECTED_ACTIVE, current);
            }
        }
        if (current.immunitySeverity().isPresent()
                && tick < current.immunityUntilTick()
                && !request.severity().strongerThan(current.immunitySeverity().orElseThrow())) {
            return rejected(CcApplicationOutcome.REJECTED_IMMUNITY, current);
        }

        PvpDuration pvp = pvpDuration(current, tick, request);
        if (pvp.duration() == 0) {
            return rejected(CcApplicationOutcome.REJECTED_DIMINISHING_RETURNS, pvp.runtime());
        }
        int duration = pvp.duration();
        CcApplicationOutcome outcome = CcApplicationOutcome.APPLIED;
        if (current.active().isPresent()) {
            if (request.severity().strongerThan(current.active().orElseThrow())) {
                outcome = CcApplicationOutcome.REPLACED;
            } else {
                outcome = CcApplicationOutcome.CONTINUED;
                duration = Math.max(1, duration / 2);
            }
        }
        CcRuntime next =
                new CcRuntime(
                        Optional.of(request.severity()),
                        tick + duration,
                        request.pvp(),
                        Optional.empty(),
                        CcRuntime.NEVER,
                        pvp.runtime().pvpRepeatSeverity(),
                        pvp.runtime().pvpRepeatCount(),
                        pvp.runtime().pvpWindowUntilTick(),
                        tick);
        return new CcApplication(outcome, next, duration);
    }

    public CcRuntime tick(CcRuntime current, long tick) {
        Objects.requireNonNull(current, "current");
        if (tick < current.lastTick()) {
            throw new IllegalArgumentException("tick must be monotonic");
        }
        Optional<CcSeverity> active = current.active();
        long activeUntil = current.activeUntilTick();
        Optional<CcSeverity> immunity = current.immunitySeverity();
        long immunityUntil = current.immunityUntilTick();
        if (active.isPresent() && tick >= activeUntil) {
            CcSeverity ended = active.orElseThrow();
            if (ended.hard()) {
                immunity = Optional.of(ended);
                immunityUntil =
                        activeUntil
                                + (current.activePvp()
                                        ? PVP_HARD_CC_IMMUNITY_TICKS
                                        : PVE_HARD_CC_IMMUNITY_TICKS);
            }
            active = Optional.empty();
            activeUntil = CcRuntime.NEVER;
        }
        if (immunity.isPresent() && tick >= immunityUntil) {
            immunity = Optional.empty();
            immunityUntil = CcRuntime.NEVER;
        }
        Optional<CcSeverity> repeat = current.pvpRepeatSeverity();
        int repeatCount = current.pvpRepeatCount();
        long repeatUntil = current.pvpWindowUntilTick();
        if (repeat.isPresent() && tick >= repeatUntil) {
            repeat = Optional.empty();
            repeatCount = 0;
            repeatUntil = CcRuntime.NEVER;
        }
        return new CcRuntime(
                active,
                activeUntil,
                active.isPresent() && current.activePvp(),
                immunity,
                immunityUntil,
                repeat,
                repeatCount,
                repeatUntil,
                tick);
    }

    private PvpDuration pvpDuration(CcRuntime current, long tick, CcRequest request) {
        if (!request.pvp()) {
            return new PvpDuration(request.durationTicks(), current);
        }
        boolean sameWindow =
                current.pvpRepeatSeverity().filter(request.severity()::equals).isPresent()
                        && tick < current.pvpWindowUntilTick();
        int count = sameWindow ? current.pvpRepeatCount() + 1 : 1;
        double dr = count == 1 ? 1.0 : count == 2 ? 0.5 : 0;
        int duration =
                dr == 0
                        ? 0
                        : Math.max(
                                1,
                                (int)
                                        Math.round(
                                                request.durationTicks()
                                                        * PVP_DURATION_MULTIPLIER
                                                        * dr));
        CcRuntime next =
                new CcRuntime(
                        current.active(),
                        current.activeUntilTick(),
                        current.activePvp(),
                        current.immunitySeverity(),
                        current.immunityUntilTick(),
                        Optional.of(request.severity()),
                        count,
                        tick + PVP_REPEAT_WINDOW_TICKS,
                        tick);
        return new PvpDuration(duration, next);
    }

    private static CcApplication rejected(CcApplicationOutcome outcome, CcRuntime runtime) {
        return new CcApplication(outcome, runtime, 0);
    }

    private record PvpDuration(int duration, CcRuntime runtime) {}
}
