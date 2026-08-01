package com.branz.mmorpg.progression.knowledge;

import com.branz.mmorpg.progression.evidence.ProgressionTrack;
import com.branz.mmorpg.progression.evidence.ReadinessBand;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record LearningRequirements(
        Set<KnowledgeKey> knowledge,
        Map<ProgressionTrack, ReadinessBand> readiness,
        Set<String> worldFlags) {
    public LearningRequirements {
        knowledge = Set.copyOf(Objects.requireNonNull(knowledge, "knowledge"));
        readiness = Map.copyOf(Objects.requireNonNull(readiness, "readiness"));
        worldFlags = Set.copyOf(Objects.requireNonNull(worldFlags, "worldFlags"));
        if (worldFlags.stream().anyMatch(flag -> flag == null || flag.isBlank())) {
            throw new IllegalArgumentException("worldFlags must not contain blank values");
        }
    }

    public static LearningRequirements none() {
        return new LearningRequirements(Set.of(), Map.of(), Set.of());
    }
}
