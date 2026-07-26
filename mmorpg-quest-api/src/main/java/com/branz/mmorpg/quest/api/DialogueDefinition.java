package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Map;
import java.util.Objects;

public record DialogueDefinition(
        ContentId id,
        int version,
        String startNode,
        Map<String, DialogueNode> nodes,
        InterruptionPolicy interruptionPolicy,
        HistoryPolicy historyPolicy,
        Map<String, String> presentationDefaults) {
    public enum InterruptionPolicy {
        CANCEL_ON_MOVE, CANCEL_ON_DISTANCE, PAUSE_ON_COMBAT,
        CANCEL_ON_COMBAT, BLOCKING
    }
    public enum HistoryPolicy { NONE, LINES, LINES_AND_CHOICES }

    public DialogueDefinition {
        Objects.requireNonNull(id, "id");
        startNode = Objects.requireNonNull(startNode, "startNode").trim();
        nodes = Map.copyOf(nodes);
        Objects.requireNonNull(interruptionPolicy, "interruptionPolicy");
        Objects.requireNonNull(historyPolicy, "historyPolicy");
        presentationDefaults = Map.copyOf(presentationDefaults);
        if (version < 1 || !nodes.containsKey(startNode)) {
            throw new IllegalArgumentException("invalid dialogue");
        }
    }
}
