package com.branz.mmorpg.quest.api;

import java.util.List;
import java.util.Objects;

public record DialogueChoice(
        String id,
        String textKey,
        List<ConditionDefinition> conditions,
        String disabledReasonKey,
        List<ActionDefinition> actions,
        String next,
        boolean recordHistory) {
    public DialogueChoice {
        id = Objects.requireNonNull(id, "id").trim();
        textKey = Objects.requireNonNull(textKey, "textKey").trim();
        conditions = List.copyOf(conditions);
        disabledReasonKey = disabledReasonKey == null ? "" : disabledReasonKey;
        actions = List.copyOf(actions);
        next = Objects.requireNonNull(next, "next").trim();
        if (id.isEmpty() || textKey.isEmpty() || next.isEmpty()) {
            throw new IllegalArgumentException("invalid dialogue choice");
        }
    }
}
