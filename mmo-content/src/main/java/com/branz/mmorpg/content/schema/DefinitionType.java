package com.branz.mmorpg.content.schema;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/** Runtime definition categories. Namespace prefixes are identity contracts, not display names. */
public enum DefinitionType {
    ITEM(Set.of("weapon", "material", "food", "consumable", "equipment", "cosmetic")),
    MOVE(Set.of("move")),
    SPELL(Set.of("spell")),
    STATUS(Set.of("status")),
    SCENE(Set.of("scene")),
    CITY(Set.of("city")),
    TRADE_GOOD(Set.of("trade")),
    LIFESKILL_NODE(Set.of("node")),
    NODE_REGION(Set.of("node_region")),
    MOUNT(Set.of("mount")),
    WORKER_JOB(Set.of("worker_job")),
    TRAIT(Set.of("trait"));

    private final Set<String> namespaces;

    DefinitionType(Set<String> namespaces) {
        this.namespaces = namespaces;
    }

    public static Optional<DefinitionType> fromId(DefinitionId id) {
        String namespace = id.value().substring(0, id.value().indexOf('.'));
        return Arrays.stream(values())
                .filter(type -> type.namespaces.contains(namespace))
                .findFirst();
    }
}
