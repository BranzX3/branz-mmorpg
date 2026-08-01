package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.lifeskills.node.ResourceNodeAccessKey;
import com.branz.mmorpg.lifeskills.node.ResourceNodeId;
import com.branz.mmorpg.lifeskills.node.ResourceNodeOperation;
import com.branz.mmorpg.lifeskills.node.ResourceNodeOperationKind;
import com.branz.mmorpg.lifeskills.node.ResourceNodePhase;
import com.branz.mmorpg.lifeskills.node.ResourceNodeReservation;
import com.branz.mmorpg.lifeskills.node.ResourceNodeRuntime;
import com.branz.mmorpg.lifeskills.node.ResourceNodeSlot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class ResourceNodeStateJsonCodec {
    private final ObjectMapper mapper = new ObjectMapper();

    String encode(ResourceNodeRuntime runtime) {
        ObjectNode root = mapper.createObjectNode();
        root.put("nodeId", runtime.nodeId().value().toString());
        root.put("definitionId", runtime.definitionId().value());
        ArrayNode slots = root.putArray("slots");
        runtime.slots().entrySet().stream()
                .sorted(
                        java.util.Comparator.comparing(
                                entry ->
                                        entry.getKey()
                                                .owner()
                                                .map(owner -> owner.value().toString())
                                                .orElse("")))
                .forEach(entry -> encodeSlot(slots.addObject(), entry.getKey(), entry.getValue()));
        ArrayNode operations = root.putArray("operations");
        runtime.processedOperations().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry -> {
                            ObjectNode operation = operations.addObject();
                            operation.put("operationId", entry.getKey().toString());
                            operation.put("kind", entry.getValue().kind().name());
                            operation.put("signature", entry.getValue().signature());
                        });
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not encode resource-node state", exception);
        }
    }

    ResourceNodeRuntime decode(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            LinkedHashMap<ResourceNodeAccessKey, ResourceNodeSlot> slots = new LinkedHashMap<>();
            root.path("slots")
                    .forEach(
                            node -> {
                                String owner = node.path("owner").asText("");
                                ResourceNodeAccessKey key =
                                        owner.isBlank()
                                                ? ResourceNodeAccessKey.shared()
                                                : ResourceNodeAccessKey.personal(
                                                        new CharacterId(UUID.fromString(owner)));
                                slots.put(key, decodeSlot(node));
                            });
            LinkedHashMap<UUID, ResourceNodeOperation> operations = new LinkedHashMap<>();
            root.path("operations")
                    .forEach(
                            node ->
                                    operations.put(
                                            UUID.fromString(node.path("operationId").asText()),
                                            new ResourceNodeOperation(
                                                    ResourceNodeOperationKind.valueOf(
                                                            node.path("kind").asText()),
                                                    node.path("signature").asText())));
            return new ResourceNodeRuntime(
                    new ResourceNodeId(UUID.fromString(root.path("nodeId").asText())),
                    DefinitionId.of(root.path("definitionId").asText()),
                    slots,
                    operations);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid durable resource-node state", exception);
        }
    }

    private static void encodeSlot(
            ObjectNode target, ResourceNodeAccessKey key, ResourceNodeSlot slot) {
        target.put("owner", key.owner().map(owner -> owner.value().toString()).orElse(""));
        target.put("phase", slot.phase().name());
        target.put("remainingCharges", slot.remainingCharges());
        slot.recoversAt().ifPresent(value -> target.put("recoversAt", value.toString()));
        slot.reservation()
                .ifPresent(
                        reservation -> {
                            ObjectNode value = target.putObject("reservation");
                            value.put("reservationId", reservation.reservationId().toString());
                            value.put("owner", reservation.owner().value().toString());
                            value.put("toolItemId", reservation.toolItemId().toString());
                            value.put("yieldSeed", reservation.yieldSeed().toString());
                            value.put("durabilityCost", reservation.durabilityCost());
                            value.put("focusCost", reservation.focusCost());
                            value.put("commitAtTick", reservation.commitAtTick());
                            value.put("reservedAt", reservation.reservedAt().toString());
                            value.put("expiresAt", reservation.expiresAt().toString());
                        });
    }

    private static ResourceNodeSlot decodeSlot(JsonNode node) {
        JsonNode reservationNode = node.get("reservation");
        Optional<ResourceNodeReservation> reservation =
                reservationNode == null || reservationNode.isNull()
                        ? Optional.empty()
                        : Optional.of(
                                new ResourceNodeReservation(
                                        UUID.fromString(
                                                reservationNode.path("reservationId").asText()),
                                        new CharacterId(
                                                UUID.fromString(
                                                        reservationNode.path("owner").asText())),
                                        UUID.fromString(
                                                reservationNode.path("toolItemId").asText()),
                                        UUID.fromString(reservationNode.path("yieldSeed").asText()),
                                        reservationNode.path("durabilityCost").asInt(),
                                        reservationNode.path("focusCost").asInt(),
                                        reservationNode.path("commitAtTick").asLong(),
                                        Instant.parse(reservationNode.path("reservedAt").asText()),
                                        Instant.parse(reservationNode.path("expiresAt").asText())));
        String recoversAt = node.path("recoversAt").asText("");
        return new ResourceNodeSlot(
                ResourceNodePhase.valueOf(node.path("phase").asText()),
                node.path("remainingCharges").asInt(),
                reservation,
                recoversAt.isBlank() ? Optional.empty() : Optional.of(Instant.parse(recoversAt)));
    }
}
