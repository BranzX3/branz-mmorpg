package com.branz.mmorpg.combat.dodge;

import com.branz.mmorpg.combat.input.DirectionSnapshot;
import java.util.Objects;

/** Immutable accepted dodge intent; phase is derived from authoritative server time. */
public record DodgeRuntime(DodgeProfile profile, DirectionSnapshot direction, long startTick) {
    public DodgeRuntime {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(direction, "direction");
        if (direction == DirectionSnapshot.NEUTRAL) {
            throw new IllegalArgumentException("dodge direction must not be neutral");
        }
        if (startTick < 0) {
            throw new IllegalArgumentException("startTick must not be negative");
        }
    }

    public DodgePhase phaseAt(long tick) {
        long elapsed = elapsed(tick);
        if (elapsed == 0) {
            return DodgePhase.STARTUP;
        }
        if (elapsed <= profile.iframeTicks()) {
            return DodgePhase.INVULNERABLE;
        }
        if (elapsed < profile.totalTicks()) {
            return DodgePhase.RECOVERY;
        }
        return DodgePhase.COMPLETE;
    }

    public boolean movementAppliesAt(long tick) {
        return elapsed(tick) < profile.movementTicks();
    }

    public long elapsed(long tick) {
        if (tick < startTick) {
            throw new IllegalArgumentException("tick must not precede dodge start");
        }
        return tick - startTick;
    }
}
