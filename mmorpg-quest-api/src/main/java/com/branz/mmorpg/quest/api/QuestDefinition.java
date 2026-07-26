package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record QuestDefinition(
        ContentId id,
        int version,
        String titleKey,
        String descriptionKey,
        String category,
        RepeatPolicy repeatPolicy,
        List<ConditionDefinition> requirements,
        String startTrigger,
        String startStage,
        Map<String, QuestStageDefinition> stages,
        List<ActionDefinition> rewards,
        MigrationPolicy migrationPolicy,
        Set<String> tags,
        int trackingPriority) {
    public enum RepeatPolicy { NEVER, DAILY, UNLIMITED }
    public enum MigrationPolicy { SAFE, REQUIRES_MAPPING, BREAKING }

    public QuestDefinition {
        Objects.requireNonNull(id, "id");
        if (version < 1) throw new IllegalArgumentException("quest version must be positive");
        titleKey = text(titleKey, "title");
        descriptionKey = text(descriptionKey, "description");
        category = text(category, "category");
        Objects.requireNonNull(repeatPolicy, "repeatPolicy");
        requirements = List.copyOf(requirements);
        startTrigger = text(startTrigger, "startTrigger");
        startStage = text(startStage, "startStage");
        stages = Map.copyOf(stages);
        rewards = List.copyOf(rewards);
        Objects.requireNonNull(migrationPolicy, "migrationPolicy");
        tags = Set.copyOf(tags);
        if (!stages.containsKey(startStage) || trackingPriority < 0) {
            throw new IllegalArgumentException("invalid quest start/tracking");
        }
    }

    private static String text(String value, String label) {
        value = Objects.requireNonNull(value, label).trim();
        if (value.isEmpty()) throw new IllegalArgumentException(label + " is blank");
        return value;
    }
}
