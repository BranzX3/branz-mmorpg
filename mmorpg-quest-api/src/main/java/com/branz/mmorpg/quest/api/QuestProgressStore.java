package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

public interface QuestProgressStore {
    Optional<QuestProgress> load(UUID playerId, ContentId questId);
    Collection<QuestProgress> active(UUID playerId);
    default Collection<QuestProgress> active(
            UUID playerId, Set<ContentId> questCandidates) {
        return active(playerId).stream()
                .filter(value -> questCandidates.contains(value.questId())).toList();
    }
    QuestCommit insert(QuestProgress progress, Collection<PendingQuestOperation> operations);
    QuestCommit commit(QuestProgress before, QuestProgress after, UUID eventId,
                       Collection<PendingQuestOperation> operations);
    Collection<PendingQuestOperation> pending(Instant dueAt, int limit);
    void completeOperation(String operationId);
    void failOperation(String operationId, String error, Instant retryAt);
    boolean hasIncompleteOperations(UUID playerId, ContentId questId);
    default boolean hasIncompleteRequiredOperations(
            UUID playerId, ContentId questId) {
        return hasIncompleteOperations(playerId, questId);
    }
    default boolean hasEarlierIncompleteRequiredOperation(
            PendingQuestOperation operation) {
        return false;
    }
    boolean reset(UUID playerId, ContentId questId, UUID actorId, String reason);
    default QuestProgress migrate(
            QuestProgress before, QuestProgress after, UUID actorId, String reason) {
        throw new UnsupportedOperationException("quest migration is not supported");
    }
    default QuestProgress repair(
            QuestProgress before, QuestProgress after,
            Collection<PendingQuestOperation> operations,
            UUID actorId, String action, String reason) {
        throw new UnsupportedOperationException("quest repair is not supported");
    }
}
