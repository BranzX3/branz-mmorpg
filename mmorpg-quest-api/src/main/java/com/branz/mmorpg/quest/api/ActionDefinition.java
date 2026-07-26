package com.branz.mmorpg.quest.api;

import java.util.Map;
import java.util.Objects;

public record ActionDefinition(
        String id,
        Type type,
        Map<String, String> values,
        Map<String, Long> numbers,
        boolean required,
        boolean idempotent,
        boolean paperThread,
        boolean reversible) {
    public enum Type {
        SET_FLAG, REMOVE_FLAG, START_QUEST, ADVANCE_QUEST, COMPLETE_QUEST,
        GRANT_ITEM, TAKE_ITEM, GRANT_CURRENCY, GRANT_MASTERY_XP, UNLOCK_CONTENT,
        START_DIALOGUE, START_CUTSCENE, ACTIVATE_TRACKER, TELEPORT,
        START_ENCOUNTER, SPAWN_ACTOR, DESPAWN_ACTOR, PLAY_SOUND, PLAY_EFFECT
    }
    public ActionDefinition {
        id = Objects.requireNonNull(id, "id").trim();
        Objects.requireNonNull(type, "type");
        values = Map.copyOf(values);
        numbers = Map.copyOf(numbers);
        if (id.isEmpty()) throw new IllegalArgumentException("action id is blank");
    }
}
