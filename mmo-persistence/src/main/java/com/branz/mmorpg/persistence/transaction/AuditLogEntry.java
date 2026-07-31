package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.TransactionId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AuditLogEntry(
        long auditId,
        TransactionId transactionId,
        Optional<CharacterId> actorCharacterId,
        String actionType,
        AuditSubjectType subjectType,
        UUID subjectId,
        String detailsJson,
        Instant createdAt) {
    public AuditLogEntry {
        if (auditId < 1) {
            throw new IllegalArgumentException("auditId must be positive");
        }
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(actorCharacterId, "actorCharacterId");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(subjectType, "subjectType");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(detailsJson, "detailsJson");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
