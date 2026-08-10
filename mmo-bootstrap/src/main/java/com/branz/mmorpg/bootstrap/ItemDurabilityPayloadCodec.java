package com.branz.mmorpg.bootstrap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/** Preserves generic item fields while replacing authoritative durability state. */
final class ItemDurabilityPayloadCodec {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ItemDurabilityPayloadCodec() {}

    static ItemDurability decode(String payloadJson, int baseMaximum) {
        if (baseMaximum < 1) {
            throw new IllegalArgumentException("base item durability must be positive");
        }
        JsonNode root = parseObject(payloadJson);
        JsonNode durability = root.get("durability");
        if (durability == null || durability.isNull()) {
            return new ItemDurability(baseMaximum, baseMaximum);
        }
        if (!durability.isObject()
                || !durability.path("currentDurability").canConvertToInt()
                || !durability.path("maximumDurability").canConvertToInt()) {
            throw new IllegalArgumentException("Persisted item durability payload is malformed");
        }
        ItemDurability state =
                new ItemDurability(
                        durability.path("currentDurability").intValue(),
                        durability.path("maximumDurability").intValue());
        if (state.maximum() != baseMaximum) {
            throw new IllegalArgumentException(
                    "Persisted item maximum does not match the active item definition");
        }
        return state;
    }

    static String encode(String payloadJson, ItemDurability durability) {
        ObjectNode root = (ObjectNode) parseObject(payloadJson);
        long displayRevision = root.path("displayRevision").asLong(1);
        if (displayRevision < 1 || displayRevision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("displayRevision cannot advance");
        }
        root.put("displayRevision", displayRevision + 1);
        ItemDurability required = Objects.requireNonNull(durability, "durability");
        ObjectNode durabilityNode = root.putObject("durability");
        durabilityNode.put("currentDurability", required.current());
        durabilityNode.put("maximumDurability", required.maximum());
        try {
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Item payload cannot be encoded", exception);
        }
    }

    private static JsonNode parseObject(String payloadJson) {
        Objects.requireNonNull(payloadJson, "payloadJson");
        try {
            JsonNode root = JSON.readTree(payloadJson);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Persisted item payload must be an object");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Persisted item payload is invalid JSON", exception);
        }
    }
}
