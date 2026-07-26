package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.util.List;
import java.util.UUID;

public interface DialogueHistoryStore {
    void append(DialogueHistoryEntry entry);
    List<DialogueHistoryEntry> read(UUID playerId, ContentId dialogueId, int limit);
}
