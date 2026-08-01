package com.branz.mmorpg.persistence.progression;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.progression.knowledge.KnowledgeAcquisitionSourceType;
import com.branz.mmorpg.progression.knowledge.KnowledgeKey;
import com.branz.mmorpg.progression.knowledge.KnowledgeType;
import java.util.Objects;
import java.util.UUID;

/** Immutable durable intent emitted after an authored acquisition source succeeds. */
public record KnowledgeAcquisitionRequest(
        UUID acquisitionId,
        CharacterId characterId,
        KnowledgeKey knowledge,
        KnowledgeAcquisitionSourceType sourceType,
        DefinitionId sourceId,
        String contentVersion) {
    public KnowledgeAcquisitionRequest {
        Objects.requireNonNull(acquisitionId, "acquisitionId");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        if (knowledge.type() != KnowledgeType.FORM && knowledge.type() != KnowledgeType.SPELL) {
            throw new IllegalArgumentException("V1 content acquisition grants Form or Spell only");
        }
        if (contentVersion.isBlank()) {
            throw new IllegalArgumentException("contentVersion must not be blank");
        }
    }
}
