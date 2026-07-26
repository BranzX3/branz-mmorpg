package com.branz.mmorpg.api.encounter;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Blocking durable encounter state port. */
public interface EncounterRepository {
    EncounterSnapshot insert(EncounterSnapshot encounter);
    Optional<EncounterSnapshot> find(UUID instanceId);
    Collection<EncounterSnapshot> recoverable();
    EncounterSnapshot save(EncounterSnapshot encounter);
}
