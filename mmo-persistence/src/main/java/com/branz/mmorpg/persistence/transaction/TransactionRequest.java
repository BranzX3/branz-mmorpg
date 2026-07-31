package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.SessionId;
import com.branz.mmorpg.api.identity.TransactionId;
import java.util.Objects;
import java.util.Optional;

public record TransactionRequest(
        TransactionId transactionId,
        String idempotencyKey,
        Optional<CharacterId> characterId,
        Optional<SessionId> sessionId,
        String operationType,
        String reservedInputsJson,
        String intendedOutputsJson,
        String contentVersion) {
    public TransactionRequest {
        Objects.requireNonNull(transactionId, "transactionId");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(sessionId, "sessionId");
        operationType = requireText(operationType, "operationType");
        reservedInputsJson = requireText(reservedInputsJson, "reservedInputsJson");
        intendedOutputsJson = requireText(intendedOutputsJson, "intendedOutputsJson");
        contentVersion = requireText(contentVersion, "contentVersion");
    }

    public static TransactionRequest forCharacter(
            TransactionId transactionId,
            String idempotencyKey,
            CharacterId characterId,
            SessionId sessionId,
            String operationType,
            String reservedInputsJson,
            String intendedOutputsJson,
            String contentVersion) {
        return new TransactionRequest(
                transactionId,
                idempotencyKey,
                Optional.of(Objects.requireNonNull(characterId, "characterId")),
                Optional.of(Objects.requireNonNull(sessionId, "sessionId")),
                operationType,
                reservedInputsJson,
                intendedOutputsJson,
                contentVersion);
    }

    public static TransactionRequest system(
            TransactionId transactionId,
            String idempotencyKey,
            String operationType,
            String reservedInputsJson,
            String intendedOutputsJson,
            String contentVersion) {
        return new TransactionRequest(
                transactionId,
                idempotencyKey,
                Optional.empty(),
                Optional.empty(),
                operationType,
                reservedInputsJson,
                intendedOutputsJson,
                contentVersion);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
