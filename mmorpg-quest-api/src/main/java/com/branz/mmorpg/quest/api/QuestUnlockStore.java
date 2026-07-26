package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.util.UUID;

public interface QuestUnlockStore {
    boolean unlocked(UUID playerId, ContentId contentId);
    boolean unlock(UUID playerId, ContentId contentId, String operationId);
}
