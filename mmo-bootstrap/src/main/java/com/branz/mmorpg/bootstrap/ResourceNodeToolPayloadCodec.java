package com.branz.mmorpg.bootstrap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import java.util.UUID;

final class ResourceNodeToolPayloadCodec {
    private final ObjectMapper mapper = new ObjectMapper();

    int durability(String json, int defaultDurability) {
        try {
            return mapper.readTree(json).path("lifeskillDurability").asInt(defaultDurability);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("invalid resource-node tool payload", exception);
        }
    }

    Optional<UUID> reservation(String json) {
        try {
            String value = mapper.readTree(json).path("nodeReservationId").asText("");
            return value.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(value));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid resource-node tool reservation", exception);
        }
    }

    String reserve(String json, int defaultDurability, UUID reservationId) {
        ObjectNode root = parseObject(json);
        root.put("lifeskillDurability", durability(json, defaultDurability));
        root.put("nodeReservationId", reservationId.toString());
        incrementDisplayRevision(root);
        return write(root);
    }

    String release(String json, int defaultDurability) {
        ObjectNode root = parseObject(json);
        root.put("lifeskillDurability", durability(json, defaultDurability));
        root.remove("nodeReservationId");
        incrementDisplayRevision(root);
        return write(root);
    }

    String spend(String json, int defaultDurability, int cost) {
        ObjectNode root = parseObject(json);
        int current = durability(json, defaultDurability);
        if (cost < 1 || current < cost) {
            throw new IllegalArgumentException("resource-node tool durability is insufficient");
        }
        root.put("lifeskillDurability", current - cost);
        root.remove("nodeReservationId");
        incrementDisplayRevision(root);
        return write(root);
    }

    private ObjectNode parseObject(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            if (!(root instanceof ObjectNode object)) {
                throw new IllegalArgumentException("resource-node tool payload must be an object");
            }
            return object;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("invalid resource-node tool payload", exception);
        }
    }

    private static void incrementDisplayRevision(ObjectNode root) {
        root.put("displayRevision", Math.addExact(root.path("displayRevision").asLong(0), 1));
    }

    private String write(ObjectNode root) {
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "could not encode resource-node tool payload", exception);
        }
    }
}
