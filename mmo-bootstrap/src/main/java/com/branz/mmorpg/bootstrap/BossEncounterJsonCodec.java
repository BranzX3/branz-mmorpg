package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.worldloop.encounter.BossEncounterPhase;
import com.branz.mmorpg.worldloop.encounter.BossEncounterRuntime;
import com.branz.mmorpg.worldloop.encounter.EncounterOperationKind;
import com.branz.mmorpg.worldloop.encounter.EncounterParticipant;
import com.branz.mmorpg.worldloop.encounter.EncounterParticipantStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Canonical V1 JSON codec for restart-safe boss encounter runtime. */
final class BossEncounterJsonCodec {
    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper mapper = new ObjectMapper();

    String encode(BossEncounterRuntime runtime) {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("encounterId", runtime.encounterId().value().toString());
        root.put("definitionId", runtime.definitionId().value());
        root.put("checkpointInstanceId", runtime.checkpointInstanceId().toString());
        root.put("phase", runtime.phase().name());
        root.put("attempt", runtime.attempt());
        root.put("startedTick", runtime.startedTick());
        ArrayNode participants = root.putArray("participants");
        runtime.participants().values().stream()
                .sorted(Comparator.comparing(value -> value.characterId().value()))
                .forEach(
                        participant -> {
                            ObjectNode node = participants.addObject();
                            node.put("characterId", participant.characterId().value().toString());
                            node.put("status", participant.status().name());
                            node.put("graceDeadlineTick", participant.graceDeadlineTick());
                        });
        ArrayNode operations = root.putArray("processedOperations");
        runtime.processedOperations().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry -> {
                            ObjectNode node = operations.addObject();
                            node.put("operationId", entry.getKey().toString());
                            node.put("kind", entry.getValue().name());
                        });
        putNullableUuid(root, "activeResetOperationId", runtime.activeResetOperationId());
        putNullableUuid(root, "rewardGrantId", runtime.rewardGrantId());
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Boss encounter state could not be encoded", exception);
        }
    }

    BossEncounterRuntime decode(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            if (!root.isObject() || requiredInt(root, "schemaVersion") != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported boss encounter schema version");
            }
            HashMap<CharacterId, EncounterParticipant> participants = new HashMap<>();
            JsonNode participantNodes = requiredArray(root, "participants");
            for (JsonNode node : participantNodes) {
                CharacterId characterId = new CharacterId(requiredUuid(node, "characterId"));
                EncounterParticipant participant =
                        new EncounterParticipant(
                                characterId,
                                requiredEnum(node, "status", EncounterParticipantStatus.class),
                                requiredLong(node, "graceDeadlineTick"));
                if (participants.put(characterId, participant) != null) {
                    throw new IllegalArgumentException("Duplicate encounter participant");
                }
            }
            HashMap<UUID, EncounterOperationKind> operations = new HashMap<>();
            JsonNode operationNodes = requiredArray(root, "processedOperations");
            for (JsonNode node : operationNodes) {
                UUID operationId = requiredUuid(node, "operationId");
                EncounterOperationKind kind =
                        requiredEnum(node, "kind", EncounterOperationKind.class);
                if (operations.put(operationId, kind) != null) {
                    throw new IllegalArgumentException("Duplicate encounter operation");
                }
            }
            return new BossEncounterRuntime(
                    new EncounterId(requiredUuid(root, "encounterId")),
                    DefinitionId.of(requiredText(root, "definitionId")),
                    requiredUuid(root, "checkpointInstanceId"),
                    requiredEnum(root, "phase", BossEncounterPhase.class),
                    requiredInt(root, "attempt"),
                    requiredLong(root, "startedTick"),
                    participants,
                    operations,
                    optionalUuid(root, "activeResetOperationId"),
                    optionalUuid(root, "rewardGrantId"));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid boss encounter state JSON", exception);
        }
    }

    private static void putNullableUuid(
            ObjectNode root, String field, Optional<UUID> optionalValue) {
        if (optionalValue.isPresent()) {
            root.put(field, optionalValue.orElseThrow().toString());
        } else {
            root.putNull(field);
        }
    }

    private static JsonNode requiredArray(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value;
    }

    private static String requiredText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank text");
        }
        return value.textValue();
    }

    private static UUID requiredUuid(JsonNode root, String field) {
        return UUID.fromString(requiredText(root, field));
    }

    private static Optional<UUID> optionalUuid(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(requiredText(root, field)));
    }

    private static int requiredInt(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return value.intValue();
    }

    private static long requiredLong(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw new IllegalArgumentException(field + " must be a long");
        }
        return value.longValue();
    }

    private static <E extends Enum<E>> E requiredEnum(
            JsonNode root, String field, Class<E> enumType) {
        return Enum.valueOf(enumType, requiredText(root, field));
    }
}
