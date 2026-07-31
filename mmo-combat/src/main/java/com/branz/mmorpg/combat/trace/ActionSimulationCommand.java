package com.branz.mmorpg.combat.trace;

import java.util.Objects;

public record ActionSimulationCommand(int tick, Type type, String detail) {
    public ActionSimulationCommand {
        if (tick < 0) {
            throw new IllegalArgumentException("command tick must not be negative");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(detail, "detail");
        if (detail.contains("\n") || detail.contains("\r")) {
            throw new IllegalArgumentException("command detail must be one line");
        }
    }

    public enum Type {
        CANCEL
    }
}
