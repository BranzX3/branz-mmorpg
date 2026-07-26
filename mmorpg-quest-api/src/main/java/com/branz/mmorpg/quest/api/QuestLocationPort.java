package com.branz.mmorpg.quest.api;

import java.util.Optional;
import java.util.UUID;

public interface QuestLocationPort {
    boolean exists(String locationId);
    boolean teleport(UUID playerId, String locationId);
    Optional<String> currentRegion(UUID playerId);
    double distance(UUID playerId, String locationId);
}
