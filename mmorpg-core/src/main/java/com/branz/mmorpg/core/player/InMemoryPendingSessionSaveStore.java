package com.branz.mmorpg.core.player;

import com.branz.mmorpg.api.player.PendingSessionSave;
import com.branz.mmorpg.api.player.PendingSessionSaveStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Test/default adapter. Production Paper wiring uses the durable file store. */
final class InMemoryPendingSessionSaveStore implements PendingSessionSaveStore {

    private final Map<UUID, PendingSessionSave> entries = new ConcurrentHashMap<>();

    @Override
    public Map<UUID, PendingSessionSave> loadAll() {
        return Map.copyOf(entries);
    }

    @Override
    public void put(PendingSessionSave pending) {
        entries.put(pending.playerId(), pending);
    }

    @Override
    public void remove(UUID playerId) {
        entries.remove(playerId);
    }
}
