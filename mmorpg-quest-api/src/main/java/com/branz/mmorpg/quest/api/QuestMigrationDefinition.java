package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Map;
import java.util.Objects;

/** Explicit mapping for one shipped quest definition version transition. */
public record QuestMigrationDefinition(
        ContentId questId,
        int fromVersion,
        int toVersion,
        Map<String, String> stageMappings,
        Map<String, String> objectiveMappings) {
    public QuestMigrationDefinition {
        Objects.requireNonNull(questId, "questId");
        stageMappings = Map.copyOf(stageMappings);
        objectiveMappings = Map.copyOf(objectiveMappings);
        if (fromVersion < 1 || toVersion <= fromVersion) {
            throw new IllegalArgumentException("invalid quest migration versions");
        }
    }
}
