package com.branz.mmorpg.combat.action;

import java.util.Objects;

public record ActionTraceEvent(int tick, ActionTraceEventType type, String detail) {
    public ActionTraceEvent {
        if (tick < 0) {
            throw new IllegalArgumentException("trace tick must not be negative");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(detail, "detail");
    }
}
