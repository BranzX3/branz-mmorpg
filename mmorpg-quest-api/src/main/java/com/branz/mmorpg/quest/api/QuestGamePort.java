package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.util.UUID;

public interface QuestGamePort {
    long itemQuantity(UUID playerId, ContentId itemId);
    int masteryLevel(UUID playerId, ContentId masteryId);
    int partySize(UUID playerId);
    boolean hasPermission(UUID playerId, String permission);
    boolean contentUnlocked(UUID playerId, ContentId contentId);
    default boolean inRegion(UUID playerId, ContentId regionId) { return false; }
    ActionResult execute(PendingQuestOperation operation);

    record ActionResult(Status status, String detail) {
        public enum Status { APPLIED, ALREADY_APPLIED, UNAVAILABLE, REJECTED }
    }
}
