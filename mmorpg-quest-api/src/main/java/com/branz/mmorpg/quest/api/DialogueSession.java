package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record DialogueSession(
        UUID sessionId,
        UUID playerId,
        ContentId dialogueId,
        int definitionVersion,
        String currentNode,
        Set<String> visitedNodes,
        Map<String, String> selectedChoices,
        long contentRevision,
        long sequence,
        State state,
        Instant startedAt,
        Instant lastActiveAt) {
    public enum State { ACTIVE, PAUSED, COMPLETE, CANCELLED }
    public DialogueSession {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(dialogueId, "dialogueId");
        currentNode = Objects.requireNonNull(currentNode, "currentNode");
        visitedNodes = Set.copyOf(visitedNodes);
        selectedChoices = Map.copyOf(selectedChoices);
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(lastActiveAt, "lastActiveAt");
        if (definitionVersion < 1 || contentRevision < 0 || sequence < 0) {
            throw new IllegalArgumentException("invalid dialogue session");
        }
    }
}
