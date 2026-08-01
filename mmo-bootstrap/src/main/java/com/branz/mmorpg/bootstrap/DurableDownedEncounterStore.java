package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.transaction.DownedEncounterStateCommit;
import com.branz.mmorpg.persistence.transaction.DownedEncounterStateCommitExecution;
import com.branz.mmorpg.persistence.transaction.DownedEncounterStateRecord;
import com.branz.mmorpg.persistence.transaction.DownedEncounterStateRepository;
import com.branz.mmorpg.persistence.transaction.JdbcDownedEncounterStateRepository;
import com.branz.mmorpg.persistence.transaction.TransactionErrorCode;
import com.branz.mmorpg.persistence.transaction.TransactionRequest;
import com.branz.mmorpg.social.downed.DownedEncounterRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Maps the immutable downed kernel to the V0010 journaled repository. */
final class DurableDownedEncounterStore {
    private final DownedEncounterStateRepository repository;
    private final String contentVersion;
    private final DownedEncounterJsonCodec codec = new DownedEncounterJsonCodec();

    DurableDownedEncounterStore(DownedEncounterStateRepository repository, String contentVersion) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
    }

    Result<StoredDownedEncounter, TransactionErrorCode> create(
            DownedEncounterRuntime runtime,
            int attempt,
            boolean recoverable,
            long recordedAtTick,
            UUID operationId) {
        return commit(runtime, attempt, recoverable, recordedAtTick, 0, operationId);
    }

    Result<StoredDownedEncounter, TransactionErrorCode> replace(
            StoredDownedEncounter expected,
            DownedEncounterRuntime replacement,
            int attempt,
            boolean recoverable,
            long recordedAtTick,
            UUID operationId) {
        Objects.requireNonNull(expected, "expected");
        if (!expected.runtime().encounterId().equals(replacement.encounterId())) {
            throw new IllegalArgumentException("Cannot replace another downed encounter");
        }
        return commit(
                replacement,
                attempt,
                recoverable,
                recordedAtTick,
                expected.record().version(),
                operationId);
    }

    Result<List<StoredDownedEncounter>, TransactionErrorCode> recoverable(long currentTick) {
        Result<List<DownedEncounterStateRecord>, TransactionErrorCode> found =
                repository.findRecoverable();
        if (found
                instanceof
                Result.Failure<List<DownedEncounterStateRecord>, TransactionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        ArrayList<StoredDownedEncounter> restored = new ArrayList<>();
        try {
            for (DownedEncounterStateRecord record :
                    ((Result.Success<List<DownedEncounterStateRecord>, TransactionErrorCode>) found)
                            .value()) {
                DecodedDownedEncounter decoded = codec.decode(record.payloadJson());
                DecodedDownedEncounter rebased = codec.rebase(decoded, currentTick);
                restored.add(
                        new StoredDownedEncounter(
                                rebased.runtime(), rebased.recordedAtTick(), record));
            }
            return Result.success(List.copyOf(restored));
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    TransactionErrorCode.TRANSACTION_INVALID_JSON,
                    "Persisted downed state is invalid: " + exception.getMessage());
        }
    }

    private Result<StoredDownedEncounter, TransactionErrorCode> commit(
            DownedEncounterRuntime runtime,
            int attempt,
            boolean recoverable,
            long recordedAtTick,
            long expectedVersion,
            UUID operationId) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(operationId, "operationId");
        String payload = codec.encode(runtime, recordedAtTick);
        TransactionRequest request =
                TransactionRequest.system(
                        new TransactionId(operationId),
                        "downed-encounter:" + runtime.encounterId().value() + ":" + operationId,
                        JdbcDownedEncounterStateRepository.DOWNED_ENCOUNTER_STATE_COMMIT,
                        "{\"encounterId\":\""
                                + runtime.encounterId().value()
                                + "\",\"expectedVersion\":"
                                + expectedVersion
                                + "}",
                        payload,
                        contentVersion);
        Result<DownedEncounterStateCommitExecution, TransactionErrorCode> committed =
                repository.commit(
                        request,
                        new DownedEncounterStateCommit(
                                runtime.encounterId(),
                                attempt,
                                recoverable,
                                expectedVersion,
                                payload));
        if (committed
                instanceof
                Result.Failure<DownedEncounterStateCommitExecution, TransactionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        DownedEncounterStateRecord record =
                ((Result.Success<DownedEncounterStateCommitExecution, TransactionErrorCode>)
                                committed)
                        .value()
                        .record();
        try {
            DecodedDownedEncounter decoded = codec.decode(record.payloadJson());
            if (record.attempt() != attempt) {
                throw new IllegalArgumentException("Committed downed attempt does not match");
            }
            return Result.success(
                    new StoredDownedEncounter(decoded.runtime(), decoded.recordedAtTick(), record));
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    TransactionErrorCode.TRANSACTION_INVALID_JSON,
                    "Committed downed state is invalid: " + exception.getMessage());
        }
    }
}
