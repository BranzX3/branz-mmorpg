package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.SessionId;
import com.branz.mmorpg.persistence.lease.CharacterLease;
import java.util.Objects;

record LoadedCharacterSession(
        CharacterId characterId,
        SessionId sessionId,
        CharacterLease lease,
        PersistentCharacterSnapshot snapshot) {
    LoadedCharacterSession {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!characterId.equals(lease.characterId()) || !sessionId.equals(lease.sessionId())) {
            throw new IllegalArgumentException("loaded session does not own its lease");
        }
    }

    LoadedCharacterSession withLease(CharacterLease nextLease) {
        return new LoadedCharacterSession(characterId, sessionId, nextLease, snapshot);
    }

    LoadedCharacterSession withSnapshot(PersistentCharacterSnapshot nextSnapshot) {
        return new LoadedCharacterSession(characterId, sessionId, lease, nextSnapshot);
    }
}
