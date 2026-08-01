package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.social.downed.DownedEncounterRuntime;
import java.util.Objects;

record DecodedDownedEncounter(DownedEncounterRuntime runtime, long recordedAtTick) {
    DecodedDownedEncounter {
        Objects.requireNonNull(runtime, "runtime");
        if (recordedAtTick < 0) {
            throw new IllegalArgumentException("recordedAtTick must not be negative");
        }
    }
}
