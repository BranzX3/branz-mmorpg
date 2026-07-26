package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public record QuestCatalog(
        long revision,
        Instant loadedAt,
        Map<ContentId, QuestDefinition> quests,
        Map<ContentId, DialogueDefinition> dialogues,
        Map<ContentId, CutsceneDefinition> cutscenes) {
    public QuestCatalog {
        quests = Map.copyOf(quests);
        dialogues = Map.copyOf(dialogues);
        cutscenes = Map.copyOf(cutscenes);
    }
    public Optional<QuestDefinition> find(ContentId id) {
        return Optional.ofNullable(quests.get(id));
    }
    public Optional<DialogueDefinition> dialogue(ContentId id) {
        return Optional.ofNullable(dialogues.get(id));
    }
    public Optional<CutsceneDefinition> cutscene(ContentId id) {
        return Optional.ofNullable(cutscenes.get(id));
    }
    public static QuestCatalog empty() {
        return new QuestCatalog(0, Instant.EPOCH, Map.of(), Map.of(), Map.of());
    }
}
