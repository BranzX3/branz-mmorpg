package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.persistence.transaction.PersonalRewardGrantRecord;
import com.branz.mmorpg.persistence.transaction.PersonalRewardGrantState;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import com.branz.mmorpg.persistence.transaction.ValueLocationType;
import com.branz.mmorpg.worldloop.reward.RewardContribution;
import com.branz.mmorpg.worldloop.reward.RewardParticipantEvidence;
import com.branz.mmorpg.worldloop.reward.RolledPersonalReward;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import java.util.UUID;

/** Canonical V1 personal grant evidence/outcome/delivery payload codec. */
final class PersonalRewardGrantJsonCodec {
    private static final int SCHEMA_VERSION = 1;
    private final ObjectMapper mapper = new ObjectMapper();

    String encode(PersonalRewardGrantPayload payload) {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("grantId", payload.grantId().toString());
        root.put("encounterId", payload.encounterId().value().toString());
        root.put("attempt", payload.attempt());
        root.put("characterId", payload.characterId().value().toString());
        root.put("rollSeed", payload.rollSeed());
        RewardParticipantEvidence evidence = payload.evidence();
        ObjectNode evidenceNode = root.putObject("evidence");
        evidenceNode.put("joinedTick", evidence.joinedTick());
        evidenceNode.put("lastActiveTick", evidence.lastActiveTick());
        evidenceNode.put("joinedBeforeEligibilityCutoff", evidence.joinedBeforeEligibilityCutoff());
        evidenceNode.put(
                "validEncounterMembershipOrRecovery",
                evidence.validEncounterMembershipOrRecovery());
        evidenceNode.put(
                "completionGrantAlreadyCommitted", evidence.completionGrantAlreadyCommitted());
        RewardContribution contribution = evidence.contribution();
        ObjectNode contributionNode = evidenceNode.putObject("contribution");
        contributionNode.put("damageAndPosture", contribution.damageAndPosture());
        contributionNode.put("guardAndControl", contribution.guardAndControl());
        contributionNode.put("healingAndSupport", contribution.healingAndSupport());
        contributionNode.put("objectiveActions", contribution.objectiveActions());
        if (payload.outcome().isPresent()) {
            RolledPersonalReward outcome = payload.outcome().orElseThrow();
            ObjectNode outcomeNode = root.putObject("outcome");
            outcomeNode.put("itemDefinitionId", outcome.itemDefinitionId().value());
            outcomeNode.put("quantity", outcome.quantity());
            outcomeNode.put("lotId", outcome.lotId().value().toString());
        } else {
            root.putNull("outcome");
        }
        if (payload.delivery().isPresent()) {
            RewardDeliveryReceipt delivery = payload.delivery().orElseThrow();
            ObjectNode deliveryNode = root.putObject("delivery");
            deliveryNode.put("transactionId", delivery.transactionId().value().toString());
            deliveryNode.put("destinationType", delivery.destination().type().name());
            delivery.destination()
                    .reference()
                    .ifPresentOrElse(
                            value -> deliveryNode.put("destinationReference", value),
                            () -> deliveryNode.putNull("destinationReference"));
        } else {
            root.putNull("delivery");
        }
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode personal reward payload", exception);
        }
    }

    PersonalRewardGrantPayload decode(PersonalRewardGrantRecord record) {
        PersonalRewardGrantPayload payload = decode(record.payloadJson(), record.state());
        if (!record.grantId().equals(payload.grantId())
                || !record.encounterId().equals(payload.encounterId())
                || record.attempt() != payload.attempt()
                || !record.characterId().equals(payload.characterId())
                || record.rollSeed() != payload.rollSeed()) {
            throw new IllegalArgumentException(
                    "reward payload identity differs from durable record");
        }
        return payload;
    }

    PersonalRewardGrantPayload decode(String json, PersonalRewardGrantState state) {
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null
                    || !root.isObject()
                    || integer(root, "schemaVersion") != SCHEMA_VERSION) {
                throw new IllegalArgumentException("unsupported personal reward payload schema");
            }
            CharacterId characterId = new CharacterId(uuid(root, "characterId"));
            JsonNode evidence = object(root, "evidence");
            JsonNode contribution = object(evidence, "contribution");
            RewardParticipantEvidence participantEvidence =
                    new RewardParticipantEvidence(
                            characterId,
                            nonNegativeLong(evidence, "joinedTick"),
                            nonNegativeLong(evidence, "lastActiveTick"),
                            bool(evidence, "joinedBeforeEligibilityCutoff"),
                            bool(evidence, "validEncounterMembershipOrRecovery"),
                            bool(evidence, "completionGrantAlreadyCommitted"),
                            new RewardContribution(
                                    nonNegativeLong(contribution, "damageAndPosture"),
                                    nonNegativeLong(contribution, "guardAndControl"),
                                    nonNegativeLong(contribution, "healingAndSupport"),
                                    nonNegativeLong(contribution, "objectiveActions")));
            Optional<RolledPersonalReward> outcome = outcome(root.get("outcome"));
            Optional<RewardDeliveryReceipt> delivery = delivery(root.get("delivery"));
            validateState(state, outcome, delivery);
            return new PersonalRewardGrantPayload(
                    uuid(root, "grantId"),
                    new EncounterId(uuid(root, "encounterId")),
                    positiveInteger(root, "attempt"),
                    characterId,
                    requiredLong(root, "rollSeed"),
                    participantEvidence,
                    outcome,
                    delivery);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("invalid personal reward payload JSON", exception);
        }
    }

    private static Optional<RolledPersonalReward> outcome(JsonNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("reward outcome must be an object or null");
        }
        return Optional.of(
                new RolledPersonalReward(
                        DefinitionId.of(text(node, "itemDefinitionId")),
                        positiveLong(node, "quantity"),
                        new LotId(uuid(node, "lotId"))));
    }

    private static Optional<RewardDeliveryReceipt> delivery(JsonNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("reward delivery must be an object or null");
        }
        ValueLocationType type;
        try {
            type = ValueLocationType.valueOf(text(node, "destinationType"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid reward destination type", exception);
        }
        JsonNode reference = node.get("destinationReference");
        Optional<String> destinationReference =
                reference == null || reference.isNull()
                        ? Optional.empty()
                        : Optional.of(text(node, "destinationReference"));
        return Optional.of(
                new RewardDeliveryReceipt(
                        new TransactionId(uuid(node, "transactionId")),
                        new ValueLocation(type, destinationReference)));
    }

    private static void validateState(
            PersonalRewardGrantState state,
            Optional<RolledPersonalReward> outcome,
            Optional<RewardDeliveryReceipt> delivery) {
        boolean valid =
                switch (state) {
                    case FROZEN -> outcome.isEmpty() && delivery.isEmpty();
                    case ROLLED -> outcome.isPresent() && delivery.isEmpty();
                    case DELIVERED -> outcome.isPresent() && delivery.isPresent();
                };
        if (!valid) {
            throw new IllegalArgumentException(
                    "reward payload does not match durable state " + state);
        }
    }

    private static JsonNode object(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return value;
    }

    private static boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.booleanValue();
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return value.intValue();
    }

    private static int positiveInteger(JsonNode node, String field) {
        int value = integer(node, field);
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException(field + " must be a long");
        }
        return value.longValue();
    }

    private static long nonNegativeLong(JsonNode node, String field) {
        long value = requiredLong(node, field);
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    private static long positiveLong(JsonNode node, String field) {
        long value = requiredLong(node, field);
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(text(node, field));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " must be a UUID", exception);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank text");
        }
        return value.textValue();
    }
}
