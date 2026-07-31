package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.combat.crossbow.CrossbowCheckpoint;
import com.branz.mmorpg.combat.crossbow.CrossbowPersistentState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;
import java.util.Optional;

/** Preserves generic item fields while replacing the unique Crossbow checkpoint payload. */
final class CrossbowPayloadCodec {
    private static final ObjectMapper JSON = new ObjectMapper();

    private CrossbowPayloadCodec() {}

    static CrossbowPersistentState decode(String payloadJson) {
        JsonNode root = parseObject(payloadJson);
        JsonNode crossbow = root.get("crossbow");
        if (crossbow == null || crossbow.isNull()) {
            return CrossbowPersistentState.unloaded();
        }
        if (!crossbow.isObject()) {
            throw new IllegalArgumentException("Persisted Crossbow payload is malformed");
        }
        CrossbowCheckpoint checkpoint;
        try {
            checkpoint = CrossbowCheckpoint.valueOf(crossbow.path("checkpoint").asText(""));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Persisted Crossbow checkpoint is invalid");
        }
        String boundAmmoValue = crossbow.path("boundAmmo").asText("");
        Optional<DefinitionId> boundAmmo =
                boundAmmoValue.isBlank()
                        ? Optional.empty()
                        : Optional.of(DefinitionId.of(boundAmmoValue));
        return new CrossbowPersistentState(checkpoint, boundAmmo);
    }

    static String encode(String payloadJson, CrossbowPersistentState state) {
        ObjectNode root = (ObjectNode) parseObject(payloadJson);
        long displayRevision = root.path("displayRevision").asLong(1);
        if (displayRevision < 1 || displayRevision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("displayRevision cannot advance");
        }
        root.put("displayRevision", displayRevision + 1);
        ObjectNode crossbow = root.putObject("crossbow");
        CrossbowPersistentState required = Objects.requireNonNull(state, "state");
        crossbow.put("checkpoint", required.checkpoint().name());
        required.boundAmmo().ifPresent(ammo -> crossbow.put("boundAmmo", ammo.value()));
        try {
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Crossbow payload cannot be encoded", exception);
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
