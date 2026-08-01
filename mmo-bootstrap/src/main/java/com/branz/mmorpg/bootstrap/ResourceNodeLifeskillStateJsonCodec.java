package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.lifeskills.progression.LifeFocusRuntime;
import com.branz.mmorpg.lifeskills.progression.LifeskillDiscipline;
import com.branz.mmorpg.lifeskills.progression.LifeskillRank;
import com.branz.mmorpg.lifeskills.progression.LifeskillRankRuntime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class ResourceNodeLifeskillStateJsonCodec {
    private final ObjectMapper mapper = new ObjectMapper();

    String encode(ResourceNodeLifeskillState state) {
        ObjectNode root = mapper.createObjectNode();
        root.put("discipline", state.rank().discipline().id().value());
        root.put("evidence", state.rank().evidence());
        root.put("rankOrdinal", state.rank().rank().ordinal());
        ArrayNode evidenceOperations = root.putArray("evidenceOperations");
        state.rank().processedOperations().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry -> {
                            ObjectNode operation = evidenceOperations.addObject();
                            operation.put("operationId", entry.getKey().toString());
                            operation.put("amount", entry.getValue());
                        });
        root.put("focus", state.focus().focus());
        root.put("lastRecoveryAt", state.focus().lastRecoveryAt().toString());
        ArrayNode focusOperations = root.putArray("focusOperations");
        state.focus().processedWorkOperations().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry -> {
                            ObjectNode operation = focusOperations.addObject();
                            operation.put("operationId", entry.getKey().toString());
                            operation.put("cost", entry.getValue());
                        });
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not encode Lifeskill state", exception);
        }
    }

    ResourceNodeLifeskillState decode(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            Map<UUID, Double> evidenceOperations = new LinkedHashMap<>();
            root.path("evidenceOperations")
                    .forEach(
                            node ->
                                    evidenceOperations.put(
                                            UUID.fromString(node.path("operationId").asText()),
                                            node.path("amount").asDouble()));
            LifeskillRankRuntime rank =
                    new LifeskillRankRuntime(
                            new LifeskillDiscipline(
                                    com.branz.mmorpg.api.identity.DefinitionId.of(
                                            root.path("discipline").asText())),
                            root.path("evidence").asDouble(),
                            LifeskillRank.fromOrdinal(root.path("rankOrdinal").asInt()),
                            evidenceOperations);
            Map<UUID, Integer> focusOperations = new LinkedHashMap<>();
            root.path("focusOperations")
                    .forEach(
                            node ->
                                    focusOperations.put(
                                            UUID.fromString(node.path("operationId").asText()),
                                            node.path("cost").asInt()));
            LifeFocusRuntime focus =
                    new LifeFocusRuntime(
                            root.path("focus").asInt(),
                            Instant.parse(root.path("lastRecoveryAt").asText()),
                            focusOperations);
            return new ResourceNodeLifeskillState(rank, focus);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid durable Lifeskill state", exception);
        }
    }
}
