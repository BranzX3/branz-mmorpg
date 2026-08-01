package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.content.definition.ContentDefinition;
import com.branz.mmorpg.content.schema.DefinitionType;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.lifeskills.node.ResourceNodeDefinition;
import com.branz.mmorpg.lifeskills.node.ResourceNodeSharing;
import com.branz.mmorpg.lifeskills.node.ResourceNodeType;
import com.branz.mmorpg.lifeskills.progression.LifeskillDiscipline;
import com.branz.mmorpg.lifeskills.progression.LifeskillRankTable;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class ResourceNodeContentCompiler {
    private ResourceNodeContentCompiler() {}

    static Optional<CompiledResourceNode> compileFirst(ContentSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return snapshot.definitions().byType(DefinitionType.LIFESKILL_NODE).stream()
                .findFirst()
                .map(ResourceNodeContentCompiler::compile);
    }

    static CompiledResourceNode compile(ContentDefinition source) {
        Objects.requireNonNull(source, "source");
        JsonNode body = source.body();
        ResourceNodeType type = ResourceNodeType.valueOf(body.path("node_type").asText());
        ResourceNodeSharing sharing =
                body.hasNonNull("sharing")
                        ? ResourceNodeSharing.valueOf(body.path("sharing").asText())
                        : defaultSharing(type);
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        JsonNode tagNodes = body.path("required_tool_tags");
        if (tagNodes.isArray()) {
            tagNodes.forEach(node -> tags.add(node.asText()));
        }
        if (tags.isEmpty()) {
            tags.add("tool.pickaxe");
        }
        JsonNode firstYield = body.path("base_yields").get(0);
        if (firstYield == null) {
            throw new IllegalArgumentException("resource node requires a base yield");
        }
        int actionTicks = body.path("action_ticks").asInt();
        int commitTick = body.path("commit_tick").asInt();
        int timeoutSeconds =
                body.path("reservation_timeout_seconds")
                        .asInt(Math.max(10, (actionTicks + 19) / 20 + 5));
        ResourceNodeDefinition definition =
                new ResourceNodeDefinition(
                        source.id(),
                        LifeskillDiscipline.of(body.path("discipline").asText("mining")),
                        type,
                        sharing,
                        body.path("maximum_charges").asInt(1),
                        commitTick,
                        Duration.ofSeconds(timeoutSeconds),
                        Duration.ofSeconds(body.path("recovery_seconds").asLong()),
                        body.path("durability_cost").asInt(1),
                        Set.copyOf(tags));
        ArrayList<Double> rankThresholds = new ArrayList<>();
        body.path("rank_thresholds").forEach(node -> rankThresholds.add(node.asDouble()));
        return new CompiledResourceNode(
                definition,
                DefinitionId.of(body.path("tool_definition").asText("equipment.training_pickaxe")),
                DefinitionId.of(firstYield.path("item").asText()),
                firstYield.path("quantity").asInt(1),
                body.path("rank_evidence").asDouble(10),
                new LifeskillRankTable(rankThresholds));
    }

    private static ResourceNodeSharing defaultSharing(ResourceNodeType type) {
        return switch (type) {
            case COMMON -> ResourceNodeSharing.PERSONAL;
            case RICH, RARE -> ResourceNodeSharing.SHARED;
            case REGIONAL, EVENT, CORRUPTED -> ResourceNodeSharing.SHARED;
        };
    }
}
