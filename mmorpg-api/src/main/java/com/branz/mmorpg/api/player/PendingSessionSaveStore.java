package com.branz.mmorpg.api.player;

import java.util.Map;
import java.util.UUID;

/**
 * Durable journal for session snapshots that could not reach the database.
 *
 * <p>Every method performs blocking I/O and must run off a Paper tick thread.
 */
public interface PendingSessionSaveStore {

    Map<UUID, PendingSessionSave> loadAll();

    void put(PendingSessionSave pending);

    void remove(UUID playerId);
}
