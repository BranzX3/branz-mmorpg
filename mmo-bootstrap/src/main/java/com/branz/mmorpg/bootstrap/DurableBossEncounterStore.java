package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.transaction.BossEncounterStateCommit;
import com.branz.mmorpg.persistence.transaction.BossEncounterStateCommitExecution;
import com.branz.mmorpg.persistence.transaction.BossEncounterStateRecord;
import com.branz.mmorpg.persistence.transaction.BossEncounterStateRepository;
import com.branz.mmorpg.persistence.transaction.JdbcBossEncounterStateRepository;
import com.branz.mmorpg.persistence.transaction.TransactionErrorCode;
import com.branz.mmorpg.persistence.transaction.TransactionRequest;
import com.branz.mmorpg.worldloop.encounter.BossEncounterRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Maps the immutable encounter kernel to the V0009 journaled repository. */
final class DurableBossEncounterStore {
    private final BossEncounterStateRepository repository;
    private final String contentVersion;
    private final BossEncounterJsonCodec codec = new BossEncounterJsonCodec();

    DurableBossEncounterStore(BossEncounterStateRepository repository, String contentVersion) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
    }

    Result<StoredBossEncounter, TransactionErrorCode> create(
            BossEncounterRuntime runtime, UUID operationId) {
        return commit(runtime, 0, operationId);
    }

    Result<StoredBossEncounter, TransactionErrorCode> replace(
            StoredBossEncounter expected, BossEncounterRuntime replacement, UUID operationId) {
        Objects.requireNonNull(expected, "expected");
        if (!expected.runtime().encounterId().equals(replacement.encounterId())) {
            throw new IllegalArgumentException("Cannot replace another encounter");
        }
        return commit(replacement, expected.record().version(), operationId);
    }

    Result<List<StoredBossEncounter>, TransactionErrorCode> recoverable() {
        Result<List<BossEncounterStateRecord>, TransactionErrorCode> found =
                repository.findRecoverable();
        if (found
                instanceof
                Result.Failure<List<BossEncounterStateRecord>, TransactionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        ArrayList<StoredBossEncounter> restored = new ArrayList<>();
        try {
            for (BossEncounterStateRecord record :
                    ((Result.Success<List<BossEncounterStateRecord>, TransactionErrorCode>) found)
                            .value()) {
                BossEncounterRuntime runtime = codec.decode(record.payloadJson());
                restored.add(new StoredBossEncounter(runtime, record));
            }
            return Result.success(List.copyOf(restored));
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    TransactionErrorCode.TRANSACTION_INVALID_JSON,
                    "Persisted boss encounter state is invalid: " + exception.getMessage());
        }
    }

    private Result<StoredBossEncounter, TransactionErrorCode> commit(
            BossEncounterRuntime runtime, long expectedVersion, UUID operationId) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(operationId, "operationId");
        String payload = codec.encode(runtime);
        TransactionRequest request =
                TransactionRequest.system(
                        new TransactionId(operationId),
                        "boss-encounter:" + runtime.encounterId().value() + ":" + operationId,
                        JdbcBossEncounterStateRepository.BOSS_ENCOUNTER_STATE_COMMIT,
                        "{\"encounterId\":\""
                                + runtime.encounterId().value()
                                + "\",\"expectedVersion\":"
                                + expectedVersion
                                + "}",
                        payload,
                        contentVersion);
        Result<BossEncounterStateCommitExecution, TransactionErrorCode> committed =
                repository.commit(
                        request,
                        new BossEncounterStateCommit(
                                runtime.encounterId(),
                                runtime.definitionId(),
                                runtime.phase().name(),
                                expectedVersion,
                                payload));
        if (committed
                instanceof
                Result.Failure<BossEncounterStateCommitExecution, TransactionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        BossEncounterStateRecord record =
                ((Result.Success<BossEncounterStateCommitExecution, TransactionErrorCode>)
                                committed)
                        .value()
                        .record();
        try {
            return Result.success(
                    new StoredBossEncounter(codec.decode(record.payloadJson()), record));
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    TransactionErrorCode.TRANSACTION_INVALID_JSON,
                    "Committed boss encounter state is invalid: " + exception.getMessage());
        }
    }
}
