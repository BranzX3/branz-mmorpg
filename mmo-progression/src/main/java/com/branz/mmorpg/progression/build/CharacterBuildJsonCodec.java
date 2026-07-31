package com.branz.mmorpg.progression.build;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Versioned deterministic JSON contract for persisted virtual build state. */
public final class CharacterBuildJsonCodec {
    private static final int SCHEMA_VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper();

    private CharacterBuildJsonCodec() {}

    public static String encode(CharacterBuild build) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("attunementCapacity", build.attunementCapacity());
        ObjectNode techniques = root.putObject("techniques");
        build.techniques().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> techniques.put(entry.getKey().name(), entry.getValue().value()));
        build.form()
                .ifPresentOrElse(
                        form -> root.put("form", form.value()), () -> root.putNull("form"));
        ArrayNode effects = root.putArray("attunedEffects");
        build.attunedEffects().stream().sorted().forEach(effect -> effects.add(effect.value()));
        try {
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot encode character build", exception);
        }
    }

    public static CharacterBuild decode(String payloadJson) {
        try {
            JsonNode root = JSON.readTree(payloadJson);
            if (!root.isObject()
                    || root.path("schemaVersion").asInt(-1) != SCHEMA_VERSION
                    || !root.path("attunementCapacity").isIntegralNumber()
                    || !root.path("techniques").isObject()
                    || !root.path("attunedEffects").isArray()) {
                throw new IllegalArgumentException("Persisted build payload has an invalid schema");
            }
            EnumMap<MovesetBranch, DefinitionId> techniques = new EnumMap<>(MovesetBranch.class);
            root.path("techniques")
                    .properties()
                    .forEach(
                            entry ->
                                    techniques.put(
                                            MovesetBranch.valueOf(entry.getKey()),
                                            definitionId(entry.getValue(), "techniques")));
            JsonNode formNode = root.path("form");
            Optional<DefinitionId> form =
                    formNode.isNull() || formNode.isMissingNode()
                            ? Optional.empty()
                            : Optional.of(definitionId(formNode, "form"));
            Set<DefinitionId> attunedEffects = new LinkedHashSet<>();
            for (JsonNode effect : root.path("attunedEffects")) {
                attunedEffects.add(definitionId(effect, "attunedEffects"));
            }
            return new CharacterBuild(
                    techniques, form, attunedEffects, root.path("attunementCapacity").intValue());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Persisted character build is invalid: " + exception.getMessage(), exception);
        }
    }

    private static DefinitionId definitionId(JsonNode node, String path) {
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalArgumentException(path + " contains a non-text definition ID");
        }
        return DefinitionId.of(node.textValue());
    }
}
