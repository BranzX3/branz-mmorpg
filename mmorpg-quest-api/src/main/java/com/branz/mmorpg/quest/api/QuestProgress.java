package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record QuestProgress(
        UUID playerId,
        ContentId questId,
        int definitionVersion,
        long revision,
        QuestState state,
        String stageId,
        UUID occurrenceId,
        Map<String, ObjectiveProgress> objectives,
        Map<String, String> flags,
        Instant startedAt,
        Instant updatedAt,
        Optional<Instant> completedAt) {
    public QuestProgress {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(state, "state");
        stageId = Objects.requireNonNull(stageId, "stageId").trim();
        Objects.requireNonNull(occurrenceId, "occurrenceId");
        objectives = Map.copyOf(objectives);
        flags = Map.copyOf(flags);
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        if (definitionVersion < 1 || revision < 0 || stageId.isEmpty()) {
            throw new IllegalArgumentException("invalid quest progress");
        }
    }
}
