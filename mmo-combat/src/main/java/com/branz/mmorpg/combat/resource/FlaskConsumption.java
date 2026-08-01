package com.branz.mmorpg.combat.resource;

import java.util.Objects;

public record FlaskConsumption(FlaskState state, FlaskRestoration restoration) {
    public FlaskConsumption {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(restoration, "restoration");
    }
}
