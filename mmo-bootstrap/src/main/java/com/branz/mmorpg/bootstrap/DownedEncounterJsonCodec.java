package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.social.downed.DownedEncounterRuntime;
import com.branz.mmorpg.social.downed.DownedOperationKind;
import com.branz.mmorpg.social.downed.DownedParticipant;
import com.branz.mmorpg.social.downed.EncounterLifeState;
import com.branz.mmorpg.social.downed.ReviveChannel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Canonical V1 JSON codec for restart-safe party downed state. */
final class DownedEncounterJsonCodec {
    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper mapper = new ObjectMapper();

    String encode(DownedEncounterRuntime runtime, long recordedAtTick) {
        if (recordedAtTick < 0) {
            throw new IllegalArgumentException("recordedAtTick must not be negative");
        }
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("encounterId", runtime.encounterId().value().toString());
        root.put("recordedAtTick", recordedAtTick);
        ArrayNode participants = root.putArray("participants");
        runtime.participants().values().stream()
                .sorted(Comparator.comparing(value -> value.characterId().value()))
                .forEach(
                        participant -> {
                            ObjectNode node = participants.addObject();
                            node.put("characterId", participant.characterId().value().toString());
                            node.put("lifeState", participant.lifeState().name());
                            node.put("reviveConsumed", participant.reviveConsumed());
                            node.put("downedDeadlineTick", participant.downedDeadlineTick());
                            node.put("protectionUntilTick", participant.protectionUntilTick());
                        });
        ArrayNode channels = root.putArray("reviveChannels");
        runtime.reviveChannelsByTarget().values().stream()
                .sorted(Comparator.comparing(value -> value.targetId().value()))
                .forEach(
                        channel -> {
                            ObjectNode node = channels.addObject();
                            node.put("channelId", channel.channelId().toString());
                            node.put("reviverId", channel.reviverId().value().toString());
                            node.put("targetId", channel.targetId().value().toString());
                            node.put("startedTick", channel.startedTick());
                            node.put("commitTick", channel.commitTick());
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
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Downed state could not be encoded", exception);
        }
    }

    DecodedDownedEncounter decode(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            if (!root.isObject() || requiredInt(root, "schemaVersion") != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported downed-state schema version");
            }
            HashMap<CharacterId, DownedParticipant> participants = new HashMap<>();
            for (JsonNode node : requiredArray(root, "participants")) {
                CharacterId characterId = new CharacterId(requiredUuid(node, "characterId"));
                DownedParticipant participant =
                        new DownedParticipant(
                                characterId,
                                requiredEnum(node, "lifeState", EncounterLifeState.class),
                                requiredBoolean(node, "reviveConsumed"),
                                requiredLong(node, "downedDeadlineTick"),
                                requiredLong(node, "protectionUntilTick"));
                if (participants.put(characterId, participant) != null) {
                    throw new IllegalArgumentException("Duplicate downed participant");
                }
            }
            HashMap<CharacterId, ReviveChannel> channels = new HashMap<>();
            for (JsonNode node : requiredArray(root, "reviveChannels")) {
                ReviveChannel channel =
                        new ReviveChannel(
                                requiredUuid(node, "channelId"),
                                new CharacterId(requiredUuid(node, "reviverId")),
                                new CharacterId(requiredUuid(node, "targetId")),
                                requiredLong(node, "startedTick"),
                                requiredLong(node, "commitTick"));
                if (channels.put(channel.targetId(), channel) != null) {
                    throw new IllegalArgumentException("Duplicate revive target");
                }
            }
            HashMap<UUID, DownedOperationKind> operations = new HashMap<>();
            for (JsonNode node : requiredArray(root, "processedOperations")) {
                UUID operationId = requiredUuid(node, "operationId");
                DownedOperationKind kind = requiredEnum(node, "kind", DownedOperationKind.class);
                if (operations.put(operationId, kind) != null) {
                    throw new IllegalArgumentException("Duplicate downed-state operation");
                }
            }
            return new DecodedDownedEncounter(
                    new DownedEncounterRuntime(
                            new EncounterId(requiredUuid(root, "encounterId")),
                            participants,
                            channels,
                            operations),
                    requiredLong(root, "recordedAtTick"));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid downed-state JSON", exception);
        }
    }

    DecodedDownedEncounter rebase(DecodedDownedEncounter stored, long currentTick) {
        if (currentTick < 0) {
            throw new IllegalArgumentException("currentTick must not be negative");
        }
        long recordedAt = stored.recordedAtTick();
        HashMap<CharacterId, DownedParticipant> participants = new HashMap<>();
        stored.runtime()
                .participants()
                .forEach(
                        (characterId, participant) ->
                                participants.put(
                                        characterId,
                                        new DownedParticipant(
                                                characterId,
                                                participant.lifeState(),
                                                participant.reviveConsumed(),
                                                rebaseDeadline(
                                                        participant.downedDeadlineTick(),
                                                        recordedAt,
                                                        currentTick),
                                                rebaseDeadline(
                                                        participant.protectionUntilTick(),
                                                        recordedAt,
                                                        currentTick))));
        HashMap<CharacterId, ReviveChannel> channels = new HashMap<>();
        stored.runtime()
                .reviveChannelsByTarget()
                .forEach(
                        (targetId, channel) ->
                                channels.put(
                                        targetId,
                                        new ReviveChannel(
                                                channel.channelId(),
                                                channel.reviverId(),
                                                targetId,
                                                Math.max(
                                                        0,
                                                        currentTick
                                                                - Math.max(
                                                                        0,
                                                                        recordedAt
                                                                                - channel
                                                                                        .startedTick())),
                                                rebaseDeadline(
                                                        channel.commitTick(),
                                                        recordedAt,
                                                        currentTick))));
        return new DecodedDownedEncounter(
                new DownedEncounterRuntime(
                        stored.runtime().encounterId(),
                        participants,
                        channels,
                        stored.runtime().processedOperations()),
                currentTick);
    }

    private static long rebaseDeadline(long deadline, long recordedAt, long currentTick) {
        if (deadline < 0) {
            return deadline;
        }
        return Math.addExact(currentTick, Math.max(0, deadline - recordedAt));
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

    private static boolean requiredBoolean(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.booleanValue();
    }

    private static <E extends Enum<E>> E requiredEnum(
            JsonNode root, String field, Class<E> enumType) {
        return Enum.valueOf(enumType, requiredText(root, field));
    }
}
