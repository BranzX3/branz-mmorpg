package com.branz.mmorpg.combat.cc;

import java.util.Objects;
import java.util.Optional;

public record CcRuntime(
        Optional<CcSeverity> active,
        long activeUntilTick,
        boolean activePvp,
        Optional<CcSeverity> immunitySeverity,
        long immunityUntilTick,
        Optional<CcSeverity> pvpRepeatSeverity,
        int pvpRepeatCount,
        long pvpWindowUntilTick,
        long lastTick) {
    public static final long NEVER = -1;

    public CcRuntime {
        active = Objects.requireNonNull(active, "active");
        immunitySeverity = Objects.requireNonNull(immunitySeverity, "immunitySeverity");
        pvpRepeatSeverity = Objects.requireNonNull(pvpRepeatSeverity, "pvpRepeatSeverity");
        if (activeUntilTick < NEVER
                || immunityUntilTick < NEVER
                || pvpRepeatCount < 0
                || pvpWindowUntilTick < NEVER
                || lastTick < 0) {
            throw new IllegalArgumentException("invalid CC runtime");
        }
        if (active.isPresent() != (activeUntilTick != NEVER)) {
            throw new IllegalArgumentException("active CC and end tick must agree");
        }
        if (immunitySeverity.isPresent() != (immunityUntilTick != NEVER)) {
            throw new IllegalArgumentException("CC immunity and end tick must agree");
        }
        if (pvpRepeatSeverity.isPresent() != (pvpWindowUntilTick != NEVER)) {
            throw new IllegalArgumentException("PvP repeat category and window must agree");
        }
    }

    public static CcRuntime initial(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative");
        }
        return new CcRuntime(
                Optional.empty(),
                NEVER,
                false,
                Optional.empty(),
                NEVER,
                Optional.empty(),
                0,
                NEVER,
                tick);
    }
}
