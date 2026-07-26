package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ObjectiveDefinition(
        String id,
        Type type,
        Optional<ContentId> targetId,
        long targetAmount,
        CreditPolicy creditPolicy,
        Set<String> acceptedSources,
        Map<String, String> options) {
    public enum Type {
        TALK, KILL, DEFEAT_BOSS, COLLECT, POSSESS, CONSUME, INTERACT,
        ENTER_REGION, USE_SKILL, CRAFT, REACH_MASTERY, WAIT, CHOOSE
    }
    public enum CreditPolicy {
        PERSONAL, PARTY_IN_RANGE, ENCOUNTER_ELIGIBLE, PARTY_SHARED
    }

    public ObjectiveDefinition {
        id = Objects.requireNonNull(id, "id").trim();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(creditPolicy, "creditPolicy");
        acceptedSources = Set.copyOf(acceptedSources);
        options = Map.copyOf(options);
        if (id.isEmpty() || targetAmount < 1) {
            throw new IllegalArgumentException("invalid objective");
        }
    }
}
