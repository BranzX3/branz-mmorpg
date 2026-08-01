package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.transaction.JdbcPersonalRewardGrantRepository;
import com.branz.mmorpg.persistence.transaction.JdbcValueTransactionService;
import com.branz.mmorpg.persistence.transaction.NewLotLocation;
import com.branz.mmorpg.persistence.transaction.PersonalRewardGrantCommit;
import com.branz.mmorpg.persistence.transaction.PersonalRewardGrantCommitExecution;
import com.branz.mmorpg.persistence.transaction.PersonalRewardGrantRecord;
import com.branz.mmorpg.persistence.transaction.PersonalRewardGrantRepository;
import com.branz.mmorpg.persistence.transaction.PersonalRewardGrantState;
import com.branz.mmorpg.persistence.transaction.TransactionErrorCode;
import com.branz.mmorpg.persistence.transaction.TransactionExecution;
import com.branz.mmorpg.persistence.transaction.TransactionRequest;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import com.branz.mmorpg.persistence.transaction.ValueTransactionService;
import com.branz.mmorpg.worldloop.encounter.BossEncounterPhase;
import com.branz.mmorpg.worldloop.encounter.BossEncounterRuntime;
import com.branz.mmorpg.worldloop.reward.EncounterRewardEngine;
import com.branz.mmorpg.worldloop.reward.EncounterRewardTable;
import com.branz.mmorpg.worldloop.reward.PersonalRewardGrant;
import com.branz.mmorpg.worldloop.reward.PersonalRewardRollEngine;
import com.branz.mmorpg.worldloop.reward.RewardFreezeResult;
import com.branz.mmorpg.worldloop.reward.RewardParticipantEvidence;
import com.branz.mmorpg.worldloop.reward.RolledPersonalReward;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Blocking persist-before-effect personal reward reconciliation for a frozen boss victory. */
final class PersonalRewardReconciliationService {
    private final PersonalRewardGrantRepository grants;
    private final ValueTransactionService values;
    private final String contentVersion;
    private final Map<DefinitionId, EncounterRewardTable> tables;
    private final EncounterRewardEngine eligibility = new EncounterRewardEngine();
    private final PersonalRewardRollEngine rolls = new PersonalRewardRollEngine();
    private final PersonalRewardGrantJsonCodec codec = new PersonalRewardGrantJsonCodec();

    PersonalRewardReconciliationService(
            PersonalRewardGrantRepository grants,
            ValueTransactionService values,
            String contentVersion,
            Map<DefinitionId, EncounterRewardTable> tables) {
        this.grants = Objects.requireNonNull(grants, "grants");
        this.values = Objects.requireNonNull(values, "values");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        this.tables = Map.copyOf(Objects.requireNonNull(tables, "tables"));
    }

    Result<PersonalRewardReconciliation, TransactionErrorCode> reconcile(
            BossEncounterRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        if (runtime.phase() != BossEncounterPhase.VICTORY_PENDING) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Personal rewards require a frozen boss victory.");
        }
        EncounterRewardTable table = tables.get(runtime.definitionId());
        if (table == null) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "No reward table exists for " + runtime.definitionId() + ".");
        }
        RewardFreezeResult frozen;
        try {
            frozen =
                    eligibility.freeze(
                            runtime.encounterId(),
                            runtime.attempt(),
                            runtime.victoryTick().orElseThrow(),
                            encounterSeed(runtime),
                            table.eligibilityProfile(),
                            orderedEvidence(runtime));
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Reward eligibility evidence is invalid: " + exception.getMessage());
        }

        HashMap<CharacterId, RolledPersonalReward> delivered = new HashMap<>();
        List<PersonalRewardGrant> orderedGrants =
                frozen.grants().values().stream()
                        .sorted(Comparator.comparing(grant -> grant.characterId().value()))
                        .toList();
        for (PersonalRewardGrant grant : orderedGrants) {
            Result<PersonalRewardGrantRecord, TransactionErrorCode> reconciled =
                    reconcileGrant(runtime, table, grant);
            if (reconciled
                    instanceof
                    Result.Failure<PersonalRewardGrantRecord, TransactionErrorCode> failure) {
                return Result.failure(failure.error(), failure.detail());
            }
            PersonalRewardGrantRecord record =
                    ((Result.Success<PersonalRewardGrantRecord, TransactionErrorCode>) reconciled)
                            .value();
            try {
                delivered.put(grant.characterId(), codec.decode(record).outcome().orElseThrow());
            } catch (IllegalArgumentException exception) {
                return Result.failure(
                        TransactionErrorCode.TRANSACTION_INVALID_JSON,
                        "Delivered reward payload is invalid: " + exception.getMessage());
            }
        }
        return Result.success(
                new PersonalRewardReconciliation(batchId(runtime), delivered, frozen.rejected()));
    }

    private Result<PersonalRewardGrantRecord, TransactionErrorCode> reconcileGrant(
            BossEncounterRuntime runtime, EncounterRewardTable table, PersonalRewardGrant grant) {
        RewardParticipantEvidence evidence = runtime.rewardEvidence().get(grant.characterId());
        PersonalRewardGrantPayload frozenPayload =
                new PersonalRewardGrantPayload(
                        grant.grantId(),
                        runtime.encounterId(),
                        runtime.attempt(),
                        grant.characterId(),
                        grant.rollSeed(),
                        evidence,
                        Optional.empty(),
                        Optional.empty());
        Result<PersonalRewardGrantRecord, TransactionErrorCode> frozen =
                commit(frozenPayload, PersonalRewardGrantState.FROZEN, 0, "freeze");
        if (frozen
                instanceof
                Result.Failure<PersonalRewardGrantRecord, TransactionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        PersonalRewardGrantRecord record =
                ((Result.Success<PersonalRewardGrantRecord, TransactionErrorCode>) frozen).value();
        if (!record.contentVersion().equals(contentVersion)) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Personal reward is pinned to unavailable content version "
                            + record.contentVersion()
                            + ".");
        }
        try {
            if (record.state() == PersonalRewardGrantState.FROZEN) {
                PersonalRewardGrantPayload payload = codec.decode(record);
                RolledPersonalReward outcome = rolls.roll(table, grant);
                PersonalRewardGrantPayload rolled =
                        new PersonalRewardGrantPayload(
                                payload.grantId(),
                                payload.encounterId(),
                                payload.attempt(),
                                payload.characterId(),
                                payload.rollSeed(),
                                payload.evidence(),
                                Optional.of(outcome),
                                Optional.empty());
                Result<PersonalRewardGrantRecord, TransactionErrorCode> rollCommit =
                        commit(rolled, PersonalRewardGrantState.ROLLED, record.version(), "roll");
                if (rollCommit
                        instanceof
                        Result.Failure<PersonalRewardGrantRecord, TransactionErrorCode> failure) {
                    return Result.failure(failure.error(), failure.detail());
                }
                record =
                        ((Result.Success<PersonalRewardGrantRecord, TransactionErrorCode>)
                                        rollCommit)
                                .value();
            }
            if (record.state() == PersonalRewardGrantState.ROLLED) {
                PersonalRewardGrantPayload payload = codec.decode(record);
                RolledPersonalReward outcome = payload.outcome().orElseThrow();
                ValueLocation destination =
                        ValueLocation.pendingRewards("personal-reward:" + payload.grantId());
                TransactionRequest deliveryRequest = deliveryRequest(payload, outcome, destination);
                Result<TransactionExecution, TransactionErrorCode> valueCommit =
                        values.grantLot(
                                deliveryRequest,
                                new NewLotLocation(
                                        outcome.lotId(),
                                        outcome.itemDefinitionId(),
                                        "default",
                                        outcome.quantity(),
                                        Optional.of(payload.characterId()),
                                        destination,
                                        lineage(payload)));
                if (valueCommit
                        instanceof
                        Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
                    return Result.failure(failure.error(), failure.detail());
                }
                TransactionExecution transaction =
                        ((Result.Success<TransactionExecution, TransactionErrorCode>) valueCommit)
                                .value();
                PersonalRewardGrantPayload delivered =
                        new PersonalRewardGrantPayload(
                                payload.grantId(),
                                payload.encounterId(),
                                payload.attempt(),
                                payload.characterId(),
                                payload.rollSeed(),
                                payload.evidence(),
                                payload.outcome(),
                                Optional.of(
                                        new RewardDeliveryReceipt(
                                                transaction.journalEntry().transactionId(),
                                                destination)));
                Result<PersonalRewardGrantRecord, TransactionErrorCode> deliveryCommit =
                        commit(
                                delivered,
                                PersonalRewardGrantState.DELIVERED,
                                record.version(),
                                "delivered");
                if (deliveryCommit
                        instanceof
                        Result.Failure<PersonalRewardGrantRecord, TransactionErrorCode> failure) {
                    return Result.failure(failure.error(), failure.detail());
                }
                record =
                        ((Result.Success<PersonalRewardGrantRecord, TransactionErrorCode>)
                                        deliveryCommit)
                                .value();
            }
            if (record.state() != PersonalRewardGrantState.DELIVERED) {
                return Result.failure(
                        TransactionErrorCode.TRANSACTION_INVALID_STATE,
                        "Personal reward did not reach DELIVERED.");
            }
            codec.decode(record);
            return Result.success(record);
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    TransactionErrorCode.TRANSACTION_INVALID_JSON,
                    "Personal reward state is invalid: " + exception.getMessage());
        }
    }

    private Result<PersonalRewardGrantRecord, TransactionErrorCode> commit(
            PersonalRewardGrantPayload payload,
            PersonalRewardGrantState state,
            long expectedVersion,
            String action) {
        String json = codec.encode(payload);
        UUID operationId = operation(payload.grantId(), action);
        TransactionRequest request =
                TransactionRequest.system(
                        new TransactionId(operationId),
                        "personal-reward:" + payload.grantId() + ":" + action,
                        JdbcPersonalRewardGrantRepository.PERSONAL_REWARD_GRANT_COMMIT,
                        "{\"grantId\":\""
                                + payload.grantId()
                                + "\",\"expectedVersion\":"
                                + expectedVersion
                                + ",\"state\":\""
                                + state
                                + "\"}",
                        json,
                        contentVersion);
        Result<PersonalRewardGrantCommitExecution, TransactionErrorCode> result =
                grants.commit(
                        request,
                        new PersonalRewardGrantCommit(
                                payload.grantId(),
                                payload.encounterId(),
                                payload.attempt(),
                                payload.characterId(),
                                payload.rollSeed(),
                                state,
                                expectedVersion,
                                json));
        if (result
                instanceof
                Result.Failure<PersonalRewardGrantCommitExecution, TransactionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        return Result.success(
                ((Result.Success<PersonalRewardGrantCommitExecution, TransactionErrorCode>) result)
                        .value()
                        .record());
    }

    private TransactionRequest deliveryRequest(
            PersonalRewardGrantPayload payload,
            RolledPersonalReward outcome,
            ValueLocation destination) {
        UUID transactionId = operation(payload.grantId(), "value");
        return TransactionRequest.system(
                new TransactionId(transactionId),
                "personal-reward:" + payload.grantId() + ":value",
                JdbcValueTransactionService.LOT_GRANT,
                "{\"grantId\":\"" + payload.grantId() + "\"}",
                "{\"lotId\":\""
                        + outcome.lotId().value()
                        + "\",\"item\":\""
                        + outcome.itemDefinitionId().value()
                        + "\",\"quantity\":"
                        + outcome.quantity()
                        + ",\"destination\":\""
                        + destination.type()
                        + "\"}",
                contentVersion);
    }

    private static List<RewardParticipantEvidence> orderedEvidence(BossEncounterRuntime runtime) {
        return runtime.rewardEvidence().values().stream()
                .sorted(Comparator.comparing(evidence -> evidence.characterId().value()))
                .toList();
    }

    private static long encounterSeed(BossEncounterRuntime runtime) {
        UUID value = runtime.encounterId().value();
        return value.getMostSignificantBits() ^ value.getLeastSignificantBits();
    }

    private static UUID batchId(BossEncounterRuntime runtime) {
        return named(
                "personal-reward-batch:" + runtime.encounterId().value() + ":" + runtime.attempt());
    }

    private static UUID operation(UUID grantId, String action) {
        return named("personal-reward:" + grantId + ":" + action);
    }

    private static UUID named(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String lineage(PersonalRewardGrantPayload payload) {
        return "{\"source\":\"personal_reward\",\"grantId\":\""
                + payload.grantId()
                + "\",\"encounterId\":\""
                + payload.encounterId().value()
                + "\",\"attempt\":"
                + payload.attempt()
                + "}";
    }
}
