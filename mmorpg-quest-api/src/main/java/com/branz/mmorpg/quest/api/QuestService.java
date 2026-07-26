package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface QuestService {
    QuestProgress start(UUID playerId, ContentId questId);
    Collection<QuestProgress> process(QuestEvent event);
    QuestProgress turnIn(UUID playerId, ContentId questId);
    QuestProgress abandon(UUID playerId, ContentId questId);
    Optional<QuestProgress> progress(UUID playerId, ContentId questId);
    Collection<QuestProgress> active(UUID playerId);
    int retryPending(int limit);
    default QuestProgress migrate(
            UUID playerId, ContentId questId, UUID actorId, String reason) {
        throw new UnsupportedOperationException("quest migration is not supported");
    }
    default QuestProgress setStage(
            UUID playerId, ContentId questId, String stageId,
            UUID actorId, String reason) {
        throw new UnsupportedOperationException("quest stage repair is not supported");
    }
    default QuestProgress setObjective(
            UUID playerId, ContentId questId, String objectiveId, long value,
            UUID actorId, String reason) {
        throw new UnsupportedOperationException("quest objective repair is not supported");
    }
}
