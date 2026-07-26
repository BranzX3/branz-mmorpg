package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Optional;
import java.util.UUID;

public interface QuestActorPort {
    UUID spawn(UUID sessionId, ContentId actorDefinition, String locationId, boolean privateActor);
    void despawn(UUID actorId);
    Optional<ActorView> actor(UUID actorId);
    record ActorView(UUID actorId, ContentId definitionId, String locationId, boolean available) {}
}
