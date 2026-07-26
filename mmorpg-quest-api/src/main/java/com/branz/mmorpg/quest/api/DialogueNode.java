package com.branz.mmorpg.quest.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DialogueNode(
        String id,
        Type type,
        String speakerKey,
        String textKey,
        String portrait,
        AdvanceMode advanceMode,
        long durationMillis,
        Optional<String> next,
        List<DialogueChoice> choices,
        List<ConditionDefinition> conditions,
        List<ActionDefinition> actions,
        Optional<String> jumpTarget) {
    public enum Type { LINE, CHOICE, CONDITION, ACTION, WAIT, JUMP, END }
    public enum AdvanceMode { MANUAL, AUTO_AFTER_DURATION, EXTERNAL_SIGNAL, CHOICE }
    public DialogueNode {
        id = Objects.requireNonNull(id, "id").trim();
        Objects.requireNonNull(type, "type");
        speakerKey = speakerKey == null ? "" : speakerKey;
        textKey = textKey == null ? "" : textKey;
        portrait = portrait == null ? "" : portrait;
        Objects.requireNonNull(advanceMode, "advanceMode");
        Objects.requireNonNull(next, "next");
        choices = List.copyOf(choices);
        conditions = List.copyOf(conditions);
        actions = List.copyOf(actions);
        Objects.requireNonNull(jumpTarget, "jumpTarget");
        if (id.isEmpty() || durationMillis < 0) {
            throw new IllegalArgumentException("invalid dialogue node");
        }
    }
}
