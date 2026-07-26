package com.branz.mmorpg.quest.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CutsceneSessionStore {
    CutsceneSession saveCutscene(CutsceneSession session);
    Optional<CutsceneSession> findCutscene(UUID sessionId);
    Collection<CutsceneSession> recoverableCutscenes();
    boolean removeCutscene(UUID sessionId);
}
