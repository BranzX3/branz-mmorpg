package com.branz.mmorpg.quest.core;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.quest.api.ObjectiveDefinition;
import com.branz.mmorpg.quest.api.QuestCatalog;
import com.branz.mmorpg.quest.api.QuestEvent;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Immutable event-to-quest candidate index built once per content revision. */
public final class ObjectiveIndex {
    private final long revision;
    private final Map<QuestEvent.Type, Set<ContentId>> candidates;

    private ObjectiveIndex(long revision, Map<QuestEvent.Type, Set<ContentId>> candidates) {
        this.revision = revision;
        this.candidates = Map.copyOf(candidates);
    }

    public static ObjectiveIndex build(QuestCatalog catalog) {
        EnumMap<QuestEvent.Type, HashSet<ContentId>> mutable =
                new EnumMap<>(QuestEvent.Type.class);
        for (QuestEvent.Type type : QuestEvent.Type.values()) {
            mutable.put(type, new HashSet<>());
        }
        ObjectiveEngine engine = new ObjectiveEngine();
        catalog.quests().values().forEach(quest -> quest.stages().values()
                .forEach(stage -> stage.objectives().forEach(objective -> {
                    if (engine.query(objective)) {
                        mutable.values().forEach(ids -> ids.add(quest.id()));
                    } else {
                        mutable.get(engine.eventType(objective.type())).add(quest.id());
                    }
                })));
        EnumMap<QuestEvent.Type, Set<ContentId>> frozen =
                new EnumMap<>(QuestEvent.Type.class);
        mutable.forEach((type, ids) -> frozen.put(type, Set.copyOf(ids)));
        return new ObjectiveIndex(catalog.revision(), frozen);
    }

    public long revision() { return revision; }

    public Set<ContentId> candidates(QuestEvent.Type eventType) {
        return candidates.getOrDefault(eventType, Set.of());
    }
}
