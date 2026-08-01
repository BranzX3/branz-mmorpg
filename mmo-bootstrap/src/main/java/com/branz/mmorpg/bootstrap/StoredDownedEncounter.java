package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.persistence.transaction.DownedEncounterStateRecord;
import com.branz.mmorpg.social.downed.DownedEncounterRuntime;
import java.util.Objects;

record StoredDownedEncounter(
        DownedEncounterRuntime runtime, long recordedAtTick, DownedEncounterStateRecord record) {
    StoredDownedEncounter {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(record, "record");
        if (recordedAtTick < 0 || !runtime.encounterId().equals(record.encounterId())) {
            throw new IllegalArgumentException("Downed runtime does not match its durable record");
        }
    }
}
