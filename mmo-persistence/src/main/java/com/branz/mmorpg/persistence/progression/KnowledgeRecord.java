package com.branz.mmorpg.persistence.progression;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.progression.knowledge.KnowledgeKey;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record KnowledgeRecord(
        CharacterId characterId,
        KnowledgeKey knowledge,
        String sourceType,
        UUID sourceId,
        String contentVersion,
        Instant learnedAt) {
    public KnowledgeRecord {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(learnedAt, "learnedAt");
        if (sourceType.isBlank() || contentVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "knowledge source and content version must not be blank");
        }
    }
}
