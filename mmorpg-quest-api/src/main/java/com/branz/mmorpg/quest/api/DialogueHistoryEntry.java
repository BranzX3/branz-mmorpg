package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.UUID;

public record DialogueHistoryEntry(
        UUID playerId, ContentId dialogueId, UUID sessionId,
        long sequence, String nodeId, String speakerKey,
        String textKey, String choiceId, Instant recordedAt) {
}
