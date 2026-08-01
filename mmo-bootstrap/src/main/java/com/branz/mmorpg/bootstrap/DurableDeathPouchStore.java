package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.transaction.DeathPouchCommit;
import com.branz.mmorpg.persistence.transaction.DeathPouchCommitExecution;
import com.branz.mmorpg.persistence.transaction.DeathPouchRecord;
import com.branz.mmorpg.persistence.transaction.DeathPouchRepository;
import com.branz.mmorpg.persistence.transaction.DeathPouchState;
import com.branz.mmorpg.persistence.transaction.JdbcDeathPouchRepository;
import com.branz.mmorpg.persistence.transaction.TransactionErrorCode;
import com.branz.mmorpg.persistence.transaction.TransactionRequest;
import com.branz.mmorpg.worldloop.death.DeathPouchDraft;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Canonical V0012 state commits with stable operation identities for restart replay. */
final class DurableDeathPouchStore {
    private final DeathPouchRepository repository;
    private final String contentVersion;

    DurableDeathPouchStore(DeathPouchRepository repository, String contentVersion) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.contentVersion = requireText(contentVersion, "contentVersion");
    }

    Result<DeathPouchRecord, TransactionErrorCode> create(DeathPouchDraft draft) {
        Objects.requireNonNull(draft, "draft");
        return commit(
                new DeathPouchCommit(
                        draft.pouchId(),
                        draft.deathId(),
                        draft.ownerCharacterId(),
                        draft.amount(),
                        draft.walletDebitOperationId(),
                        draft.walletCreditOperationId(),
                        draft.location().worldKey(),
                        draft.location().x(),
                        draft.location().y(),
                        draft.location().z(),
                        draft.createdAt(),
                        draft.expiresAt(),
                        DeathPouchState.PENDING_DEBIT,
                        0,
                        payload(DeathPouchState.PENDING_DEBIT)));
    }

    Result<DeathPouchRecord, TransactionErrorCode> transition(
            DeathPouchRecord current, DeathPouchState replacement) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(replacement, "replacement");
        return commit(
                new DeathPouchCommit(
                        current.pouchId(),
                        current.deathId(),
                        current.ownerCharacterId(),
                        current.amount(),
                        current.walletDebitOperationId(),
                        current.walletCreditOperationId(),
                        current.worldKey(),
                        current.locationX(),
                        current.locationY(),
                        current.locationZ(),
                        current.createdAt(),
                        current.expiresAt(),
                        replacement,
                        current.version(),
                        payload(replacement)));
    }

    Result<List<DeathPouchRecord>, TransactionErrorCode> active(CharacterId owner) {
        return repository.findActive(owner);
    }

    Result<List<DeathPouchRecord>, TransactionErrorCode> recoverable() {
        return repository.findRecoverable();
    }

    Result<List<DeathPouchRecord>, TransactionErrorCode> expirable(Instant now) {
        return repository.findExpirable(now);
    }

    private Result<DeathPouchRecord, TransactionErrorCode> commit(DeathPouchCommit commit) {
        UUID operationId = operationId(commit.pouchId(), commit.state());
        TransactionRequest request =
                TransactionRequest.system(
                        new TransactionId(operationId),
                        "death-pouch:"
                                + commit.pouchId()
                                + ":state:"
                                + commit.state().name().toLowerCase(java.util.Locale.ROOT),
                        JdbcDeathPouchRepository.DEATH_POUCH_COMMIT,
                        "{\"pouchId\":\""
                                + commit.pouchId()
                                + "\",\"expectedVersion\":"
                                + commit.expectedVersion()
                                + "}",
                        commit.replacementPayloadJson(),
                        contentVersion);
        Result<DeathPouchCommitExecution, TransactionErrorCode> result =
                repository.commit(request, commit);
        if (result
                instanceof
                Result.Failure<DeathPouchCommitExecution, TransactionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        return Result.success(
                ((Result.Success<DeathPouchCommitExecution, TransactionErrorCode>) result)
                        .value()
                        .record());
    }

    private static String payload(DeathPouchState state) {
        return "{\"schemaVersion\":1,\"state\":\"" + state + "\"}";
    }

    private static UUID operationId(UUID pouchId, DeathPouchState state) {
        return UUID.nameUUIDFromBytes(
                ("death-pouch:" + pouchId + ":state:" + state).getBytes(StandardCharsets.UTF_8));
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
