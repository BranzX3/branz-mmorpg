package com.branz.mmorpg.progression.knowledge;

import com.branz.mmorpg.progression.evidence.ProgressionTrack;
import com.branz.mmorpg.progression.evidence.ReadinessBand;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record KnowledgeProfile(
        Set<KnowledgeKey> learned,
        Map<ProgressionTrack, ReadinessBand> readiness,
        Set<String> worldFlags) {
    public KnowledgeProfile {
        learned = Set.copyOf(Objects.requireNonNull(learned, "learned"));
        readiness = Map.copyOf(Objects.requireNonNull(readiness, "readiness"));
        worldFlags = Set.copyOf(Objects.requireNonNull(worldFlags, "worldFlags"));
    }

    public boolean knows(KnowledgeKey key) {
        return learned.contains(Objects.requireNonNull(key, "key"));
    }
}
