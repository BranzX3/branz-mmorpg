package com.branz.mmorpg.quest.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface DialogueSessionStore {
    DialogueSession save(DialogueSession session);
    Optional<DialogueSession> find(UUID sessionId);
    Collection<DialogueSession> recoverable();
    boolean remove(UUID sessionId);
}
