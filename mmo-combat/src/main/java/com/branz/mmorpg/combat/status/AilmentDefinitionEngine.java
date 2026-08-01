package com.branz.mmorpg.combat.status;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.definition.ContentDefinition;
import com.branz.mmorpg.content.schema.DefinitionType;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable runtime ailment definitions compiled from the active content snapshot. */
public final class AilmentDefinitionEngine {
    private final Map<AilmentType, AilmentDefinition> definitions;

    private AilmentDefinitionEngine(Map<AilmentType, AilmentDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public static Result<AilmentDefinitionEngine, AilmentDefinitionEngineErrorCode> compile(
            ContentSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        LinkedHashMap<AilmentType, AilmentDefinition> compiled = new LinkedHashMap<>();
        for (ContentDefinition source : snapshot.definitions().byType(DefinitionType.STATUS)) {
            try {
                AilmentDefinition definition = compileDefinition(source);
                if (compiled.putIfAbsent(definition.type(), definition) != null) {
                    throw new IllegalArgumentException(
                            "duplicate ailment type " + definition.type());
                }
            } catch (IllegalArgumentException exception) {
                return Result.failure(
                        AilmentDefinitionEngineErrorCode.AILMENT_DEFINITION_INVALID,
                        source.id().value() + ": " + exception.getMessage());
            }
        }
        return Result.success(new AilmentDefinitionEngine(compiled));
    }

    public Optional<AilmentDefinition> find(AilmentType type) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(type, "type")));
    }

    public Collection<AilmentDefinition> all() {
        return definitions.values();
    }

    private static AilmentDefinition compileDefinition(ContentDefinition source) {
        JsonNode body = source.body();
        AilmentType type = AilmentType.valueOf(requiredText(body, "ailment_type"));
        DefinitionId expectedId =
                DefinitionId.of("status." + type.name().toLowerCase(java.util.Locale.ROOT));
        if (!source.id().equals(expectedId)) {
            throw new IllegalArgumentException(
                    "ailment_type " + type + " requires definition_id " + expectedId.value());
        }
        Set<String> cleanseTags = requiredTextSet(body, "cleanse_tags");
        return new AilmentDefinition(
                type,
                requiredNumber(body, "buildup_max"),
                requiredInteger(body, "buildup_decay_delay_ticks"),
                requiredNumber(body, "buildup_decay_per_tick"),
                requiredInteger(body, "active_duration_ticks"),
                AilmentReapplication.valueOf(requiredText(body, "reapplication")),
                requiredInteger(body, "maximum_tier"),
                requiredText(body, "resistance_channel"),
                cleanseTags,
                AilmentPersistence.valueOf(requiredText(body, "persistence")),
                requiredNumber(body.path("profiles"), "pve_multiplier"),
                requiredNumber(body.path("profiles"), "pvp_multiplier"),
                requiredText(body.path("presentation"), "visual_cue"),
                requiredText(body.path("presentation"), "audio_cue"));
    }

    private static int requiredInteger(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return node.intValue();
    }

    private static double requiredNumber(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isNumber() || !Double.isFinite(node.doubleValue())) {
            throw new IllegalArgumentException(field + " must be a finite number");
        }
        return node.doubleValue();
    }

    private static String requiredText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank text");
        }
        return node.textValue();
    }

    private static Set<String> requiredTextSet(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IllegalArgumentException(field + " must contain entries");
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode value : node) {
            if (!value.isTextual() || value.textValue().isBlank()) {
                throw new IllegalArgumentException(field + " entries must be non-blank text");
            }
            values.add(value.textValue());
        }
        return values;
    }
}
