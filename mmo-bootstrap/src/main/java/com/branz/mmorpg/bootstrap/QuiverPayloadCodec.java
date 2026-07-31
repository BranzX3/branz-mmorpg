package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.items.quiver.QuiverPreparation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Objects;

/** Preserves generic item payload fields while reading/writing Quiver preparation state. */
final class QuiverPayloadCodec {
    private static final ObjectMapper JSON = new ObjectMapper();

    private QuiverPayloadCodec() {}

    static QuiverPreparation decode(String payloadJson) {
        JsonNode root = parseObject(payloadJson);
        JsonNode quiver = root.get("quiver");
        if (quiver == null || quiver.isNull()) {
            return QuiverPreparation.empty();
        }
        if (!quiver.isObject() || !quiver.path("preparedAmmo").isArray()) {
            throw new IllegalArgumentException("Persisted Quiver payload is malformed");
        }
        ArrayList<DefinitionId> prepared = new ArrayList<>();
        for (JsonNode entry : quiver.path("preparedAmmo")) {
            prepared.add(DefinitionId.of(entry.asText("")));
        }
        int selectedIndex = quiver.path("selectedIndex").asInt(Integer.MIN_VALUE);
        return new QuiverPreparation(prepared, selectedIndex);
    }

    static String encode(String payloadJson, QuiverPreparation preparation) {
        ObjectNode root = (ObjectNode) parseObject(payloadJson);
        long displayRevision = root.path("displayRevision").asLong(1);
        if (displayRevision < 1 || displayRevision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("displayRevision cannot advance");
        }
        root.put("displayRevision", displayRevision + 1);
        ObjectNode quiver = root.putObject("quiver");
        ArrayNode prepared = quiver.putArray("preparedAmmo");
        Objects.requireNonNull(preparation, "preparation")
                .preparedAmmo()
                .forEach(ammo -> prepared.add(ammo.value()));
        quiver.put("selectedIndex", preparation.selectedIndex());
        try {
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Quiver payload cannot be encoded", exception);
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
