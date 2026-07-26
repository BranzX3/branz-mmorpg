package com.branz.mmorpg.quest.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record QuestStageDefinition(
        String id,
        List<ActionDefinition> activationActions,
        List<ObjectiveDefinition> objectives,
        CompletionPolicy completionPolicy,
        int completionCount,
        List<ActionDefinition> completionActions,
        Optional<String> nextStage,
        Optional<String> failureStage,
        boolean checkpoint) {
    public enum CompletionPolicy { ALL, ANY, COUNT, EXPRESSION }

    public QuestStageDefinition {
        id = Objects.requireNonNull(id, "id").trim();
        activationActions = List.copyOf(activationActions);
        objectives = List.copyOf(objectives);
        Objects.requireNonNull(completionPolicy, "completionPolicy");
        completionActions = List.copyOf(completionActions);
        Objects.requireNonNull(nextStage, "nextStage");
        Objects.requireNonNull(failureStage, "failureStage");
        if (id.isEmpty() || objectives.isEmpty()
                || (completionPolicy == CompletionPolicy.COUNT
                && (completionCount < 1 || completionCount > objectives.size()))) {
            throw new IllegalArgumentException("invalid stage " + id);
        }
    }
}
