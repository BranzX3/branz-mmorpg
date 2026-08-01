package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.combat.resource.FlaskAllocation;
import com.branz.mmorpg.combat.resource.FlaskDose;
import com.branz.mmorpg.combat.resource.FlaskState;
import com.branz.mmorpg.combat.resource.PreparedFlaskSnapshot;
import com.branz.mmorpg.combat.status.AilmentType;
import com.branz.mmorpg.items.consumable.ConsumableCategory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Canonical restart-safe expedition-state boundary with backward V1 decoding. */
final class ExpeditionStateJsonCodec {
    private static final int SCHEMA_VERSION = 2;
    private static final ObjectMapper JSON = new ObjectMapper();

    private ExpeditionStateJsonCodec() {}

    static String encode(PersistentExpeditionState state) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        writeFlask(root.putObject("flask"), state.flaskState());
        ArrayNode effects = root.putArray("consumableEffects");
        state.consumableEffects().stream()
                .sorted(java.util.Comparator.comparing(effect -> effect.category().name()))
                .forEach(
                        effect -> {
                            ObjectNode node = effects.addObject();
                            node.put("definitionId", effect.definitionId().value());
                            node.put("category", effect.category().name());
                            node.put("remainingTicks", effect.remainingTicks());
                            node.put("rare", effect.rare());
                        });
        ArrayNode ailments = root.putArray("ailments");
        for (AilmentType type : AilmentType.values()) {
            PersistentAilmentState stateValue = state.ailments().get(type);
            if (stateValue == null) {
                continue;
            }
            ObjectNode node = ailments.addObject();
            node.put("type", type.name());
            node.put("buildup", stateValue.buildup());
            node.put("decayDelayRemainingTicks", stateValue.decayDelayRemainingTicks());
            node.put("activeRemainingTicks", stateValue.activeRemainingTicks());
            node.put("tier", stateValue.tier());
        }
        state.preparedFlaskSnapshot()
                .ifPresentOrElse(
                        snapshot -> {
                            ObjectNode node = root.putObject("preparedFlaskSnapshot");
                            node.put(
                                    "checkpointInstanceId",
                                    snapshot.checkpointInstanceId().toString());
                            writeFlask(node.putObject("flask"), snapshot.flaskState());
                        },
                        () -> root.putNull("preparedFlaskSnapshot"));
        try {
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Expedition state cannot be encoded", exception);
        }
    }

    static PersistentExpeditionState decode(String payloadJson) {
        try {
            JsonNode root = JSON.readTree(payloadJson);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Expedition state must be an object");
            }
            int schemaVersion = root.path("schemaVersion").asInt(-1);
            if (schemaVersion != 1 && schemaVersion != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported expedition-state schema");
            }
            FlaskState flask = readFlask(requiredObject(root, "flask"));

            List<PersistentConsumableEffect> effects = new ArrayList<>();
            for (JsonNode node : requiredArray(root, "consumableEffects")) {
                effects.add(
                        new PersistentConsumableEffect(
                                DefinitionId.of(requiredText(node, "definitionId")),
                                ConsumableCategory.valueOf(requiredText(node, "category")),
                                requiredInt(node, "remainingTicks"),
                                requiredBoolean(node, "rare")));
            }
            EnumMap<AilmentType, PersistentAilmentState> ailments =
                    new EnumMap<>(AilmentType.class);
            for (JsonNode node : requiredArray(root, "ailments")) {
                AilmentType type = AilmentType.valueOf(requiredText(node, "type"));
                if (ailments.put(
                                type,
                                new PersistentAilmentState(
                                        type,
                                        requiredDouble(node, "buildup"),
                                        requiredInt(node, "decayDelayRemainingTicks"),
                                        requiredInt(node, "activeRemainingTicks"),
                                        requiredInt(node, "tier")))
                        != null) {
                    throw new IllegalArgumentException("Duplicate persisted ailment type");
                }
            }
            Optional<PreparedFlaskSnapshot> preparedSnapshot = Optional.empty();
            if (schemaVersion >= 2) {
                JsonNode snapshotNode = root.get("preparedFlaskSnapshot");
                if (snapshotNode == null) {
                    throw new IllegalArgumentException("preparedFlaskSnapshot must be present");
                }
                if (!snapshotNode.isNull()) {
                    if (!snapshotNode.isObject()) {
                        throw new IllegalArgumentException(
                                "preparedFlaskSnapshot must be an object or null");
                    }
                    preparedSnapshot =
                            Optional.of(
                                    new PreparedFlaskSnapshot(
                                            UUID.fromString(
                                                    requiredText(
                                                            snapshotNode, "checkpointInstanceId")),
                                            readFlask(requiredObject(snapshotNode, "flask"))));
                }
            }
            return new PersistentExpeditionState(flask, effects, ailments, preparedSnapshot);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Persisted expedition state is invalid", exception);
        }
    }

    private static void writeFlask(ObjectNode node, FlaskState flaskState) {
        node.put("capacity", flaskState.allocation().capacity());
        ObjectNode allocation = node.putObject("allocation");
        ObjectNode charges = node.putObject("charges");
        for (FlaskDose dose : FlaskDose.values()) {
            allocation.put(dose.name(), flaskState.allocation().maximum(dose));
            charges.put(dose.name(), flaskState.charge(dose));
        }
    }

    private static FlaskState readFlask(JsonNode flaskNode) {
        int capacity = requiredInt(flaskNode, "capacity");
        JsonNode allocationNode = requiredObject(flaskNode, "allocation");
        JsonNode chargesNode = requiredObject(flaskNode, "charges");
        EnumMap<FlaskDose, Integer> allocations = new EnumMap<>(FlaskDose.class);
        EnumMap<FlaskDose, Integer> charges = new EnumMap<>(FlaskDose.class);
        for (FlaskDose dose : FlaskDose.values()) {
            allocations.put(dose, requiredInt(allocationNode, dose.name()));
            charges.put(dose, requiredInt(chargesNode, dose.name()));
        }
        return new FlaskState(new FlaskAllocation(capacity, allocations), charges);
    }

    private static JsonNode requiredObject(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return node;
    }

    private static JsonNode requiredArray(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return node;
    }

    private static String requiredText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank text");
        }
        return node.textValue();
    }

    private static int requiredInt(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return node.intValue();
    }

    private static double requiredDouble(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isNumber() || !Double.isFinite(node.doubleValue())) {
            throw new IllegalArgumentException(field + " must be a finite number");
        }
        return node.doubleValue();
    }

    private static boolean requiredBoolean(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return node.booleanValue();
    }
}
