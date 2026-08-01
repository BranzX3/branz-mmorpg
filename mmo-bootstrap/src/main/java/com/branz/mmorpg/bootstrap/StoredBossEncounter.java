package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.persistence.transaction.BossEncounterStateRecord;
import com.branz.mmorpg.worldloop.encounter.BossEncounterRuntime;
import java.util.Objects;

record StoredBossEncounter(BossEncounterRuntime runtime, BossEncounterStateRecord record) {
    StoredBossEncounter {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(record, "record");
        if (!runtime.encounterId().equals(record.encounterId())
                || !runtime.definitionId().equals(record.definitionId())
                || !runtime.phase().name().equals(record.phase())) {
            throw new IllegalArgumentException(
                    "Encounter runtime does not match its durable record");
        }
    }
}
