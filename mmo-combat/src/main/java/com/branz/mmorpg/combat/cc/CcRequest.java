package com.branz.mmorpg.combat.cc;

import java.util.Objects;

public record CcRequest(
        CcSeverity severity, int durationTicks, boolean comboContinuation, boolean pvp) {
    public CcRequest {
        Objects.requireNonNull(severity, "severity");
        if (durationTicks < 1) {
            throw new IllegalArgumentException("CC duration must be positive");
        }
    }
}
