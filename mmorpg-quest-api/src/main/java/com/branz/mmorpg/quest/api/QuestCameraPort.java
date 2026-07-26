package com.branz.mmorpg.quest.api;

import java.util.UUID;

public interface QuestCameraPort {
    void attach(UUID playerId, UUID sessionId, String cameraPathId);
    void lookAt(UUID playerId, String locationId, long durationMillis);
    void restore(UUID playerId, UUID sessionId);
}
