package com.branz.mmorpg.content.reference;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReferenceIndex {
    private final List<ContentReference> references;
    private final Map<DefinitionId, List<ContentReference>> outgoing;
    private final Map<DefinitionId, List<ContentReference>> incoming;

    private ReferenceIndex(
            List<ContentReference> references,
            Map<DefinitionId, List<ContentReference>> outgoing,
            Map<DefinitionId, List<ContentReference>> incoming) {
        this.references = List.copyOf(references);
        this.outgoing = immutableIndex(outgoing);
        this.incoming = immutableIndex(incoming);
    }

    public static ReferenceIndex of(Collection<ContentReference> references) {
        LinkedHashMap<DefinitionId, List<ContentReference>> outgoing = new LinkedHashMap<>();
        LinkedHashMap<DefinitionId, List<ContentReference>> incoming = new LinkedHashMap<>();
        for (ContentReference reference : references) {
            outgoing.computeIfAbsent(reference.sourceId(), ignored -> new ArrayList<>())
                    .add(reference);
            incoming.computeIfAbsent(reference.targetId(), ignored -> new ArrayList<>())
                    .add(reference);
        }
        return new ReferenceIndex(List.copyOf(references), outgoing, incoming);
    }

    public List<ContentReference> all() {
        return references;
    }

    public List<ContentReference> outgoing(DefinitionId id) {
        return outgoing.getOrDefault(id, List.of());
    }

    public List<ContentReference> incoming(DefinitionId id) {
        return incoming.getOrDefault(id, List.of());
    }

    private static Map<DefinitionId, List<ContentReference>> immutableIndex(
            Map<DefinitionId, List<ContentReference>> source) {
        LinkedHashMap<DefinitionId, List<ContentReference>> copy = new LinkedHashMap<>();
        source.forEach((id, references) -> copy.put(id, List.copyOf(references)));
        return Collections.unmodifiableMap(copy);
    }
}
