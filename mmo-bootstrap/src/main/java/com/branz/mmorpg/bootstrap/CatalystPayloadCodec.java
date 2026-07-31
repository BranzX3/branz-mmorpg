package com.branz.mmorpg.bootstrap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/** Preserves generic item fields while replacing catalyst durability state. */
final class CatalystPayloadCodec {
    private static final ObjectMapper JSON = new ObjectMapper();

    private CatalystPayloadCodec() {}

    static CatalystDurability decode(String payloadJson, int baseMaximum) {
        if (baseMaximum < 1) {
            throw new IllegalArgumentException("base catalyst durability must be positive");
        }
        JsonNode root = parseObject(payloadJson);
        JsonNode catalyst = root.get("catalyst");
        if (catalyst == null || catalyst.isNull()) {
            return new CatalystDurability(baseMaximum, baseMaximum);
        }
        if (!catalyst.isObject()
                || !catalyst.path("currentDurability").canConvertToInt()
                || !catalyst.path("maximumDurability").canConvertToInt()) {
            throw new IllegalArgumentException("Persisted catalyst payload is malformed");
        }
        CatalystDurability durability =
                new CatalystDurability(
                        catalyst.path("currentDurability").intValue(),
                        catalyst.path("maximumDurability").intValue());
        if (durability.maximum() != baseMaximum) {
            throw new IllegalArgumentException(
                    "Persisted catalyst maximum does not match the active item definition");
        }
        return durability;
    }

    static String encode(String payloadJson, CatalystDurability durability) {
        ObjectNode root = (ObjectNode) parseObject(payloadJson);
        long displayRevision = root.path("displayRevision").asLong(1);
        if (displayRevision < 1 || displayRevision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("displayRevision cannot advance");
        }
        root.put("displayRevision", displayRevision + 1);
        CatalystDurability required = Objects.requireNonNull(durability, "durability");
        ObjectNode catalyst = root.putObject("catalyst");
        catalyst.put("currentDurability", required.current());
        catalyst.put("maximumDurability", required.maximum());
        try {
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Catalyst payload cannot be encoded", exception);
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
