package com.branz.mmorpg.quest.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ConditionDefinition(
        Type type,
        Map<String, String> values,
        Map<String, Long> numbers,
        List<ConditionDefinition> children,
        boolean unavailableAsFalse) {
    public enum Type {
        QUEST_STATE, FLAG, ITEM_POSSESSION, MASTERY_LEVEL, PLAYER_WORLD_REGION,
        PERMISSION, TIME_WINDOW, PARTY_SIZE, CONTENT_UNLOCK, ALL, ANY, NOT
    }
    public ConditionDefinition {
        Objects.requireNonNull(type, "type");
        values = Map.copyOf(values);
        numbers = Map.copyOf(numbers);
        children = List.copyOf(children);
    }
}
