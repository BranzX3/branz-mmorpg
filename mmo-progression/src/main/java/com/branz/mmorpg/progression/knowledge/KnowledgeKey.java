package com.branz.mmorpg.progression.knowledge;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;

public record KnowledgeKey(KnowledgeType type, DefinitionId id)
        implements Comparable<KnowledgeKey> {
    public KnowledgeKey {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }

    @Override
    public int compareTo(KnowledgeKey other) {
        int typeOrder = type.compareTo(other.type);
        return typeOrder == 0 ? id.compareTo(other.id) : typeOrder;
    }
}
