package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record QuestEvent(
        UUID eventId,
        Type type,
        UUID playerId,
        java.util.Optional<ContentId> targetId,
        long amount,
        String source,
        Set<UUID> partyInRangeSnapshot,
        boolean encounterEligible,
        Map<String, String> data,
        Instant occurredAt) {
    public enum Type {
        NPC_TALKED, MOB_KILLED, BOSS_DEFEATED, ITEM_ACQUIRED,
        WORLD_OBJECT_INTERACTED, REGION_ENTERED, SKILL_USED, CRAFT_COMPLETED,
        MASTERY_CHANGED, TIMER_ELAPSED, DIALOGUE_CHOICE
    }
    public QuestEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(targetId, "targetId");
        if (amount < 0) throw new IllegalArgumentException("event amount cannot be negative");
        source = Objects.requireNonNull(source, "source").trim();
        partyInRangeSnapshot = Set.copyOf(partyInRangeSnapshot);
        data = Map.copyOf(data);
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
