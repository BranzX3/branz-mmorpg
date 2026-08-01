package com.branz.mmorpg.worldloop.encounter;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pure boss lifecycle state machine used by persistence and live-server adapters. */
public final class BossEncounterEngine {
    public static final int MAX_PARTICIPANTS = 5;
    public static final long REJOIN_GRACE_TICKS = 1_200;

    public Result<BossEncounterRuntime, BossEncounterErrorCode> start(
            EncounterId encounterId,
            DefinitionId definitionId,
            UUID checkpointInstanceId,
            Collection<CharacterId> participantIds,
            long currentTick) {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(checkpointInstanceId, "checkpointInstanceId");
        Objects.requireNonNull(participantIds, "participantIds");
        requireTick(currentTick);
        Set<CharacterId> uniqueParticipants = Set.copyOf(participantIds);
        if (uniqueParticipants.isEmpty()
                || uniqueParticipants.size() > MAX_PARTICIPANTS
                || uniqueParticipants.size() != participantIds.size()) {
            return Result.failure(
                    BossEncounterErrorCode.INVALID_PARTICIPANTS,
                    "Boss encounter requires one to five unique participants.");
        }
        HashMap<CharacterId, EncounterParticipant> participants = new HashMap<>();
        uniqueParticipants.forEach(
                characterId ->
                        participants.put(characterId, EncounterParticipant.active(characterId)));
        return Result.success(
                new BossEncounterRuntime(
                        encounterId,
                        definitionId,
                        checkpointInstanceId,
                        BossEncounterPhase.ACTIVE,
                        1,
                        currentTick,
                        participants,
                        Map.of(),
                        Optional.empty(),
                        Optional.empty()));
    }

    public Result<BossEncounterTransition, BossEncounterErrorCode> defeat(
            BossEncounterRuntime runtime,
            CharacterId characterId,
            UUID operationId,
            long currentTick) {
        return changeParticipant(
                runtime,
                characterId,
                operationId,
                EncounterOperationKind.PARTICIPANT_DEFEATED,
                EncounterParticipantStatus.DEFEATED,
                EncounterParticipant.NO_GRACE_DEADLINE,
                currentTick);
    }

    public Result<BossEncounterTransition, BossEncounterErrorCode> disconnect(
            BossEncounterRuntime runtime,
            CharacterId characterId,
            UUID operationId,
            long currentTick) {
        return changeParticipant(
                runtime,
                characterId,
                operationId,
                EncounterOperationKind.PARTICIPANT_DISCONNECTED,
                EncounterParticipantStatus.DISCONNECTED_GRACE,
                Math.addExact(currentTick, REJOIN_GRACE_TICKS),
                currentTick);
    }

    public Result<BossEncounterTransition, BossEncounterErrorCode> leaveBoundary(
            BossEncounterRuntime runtime,
            CharacterId characterId,
            UUID operationId,
            long currentTick) {
        return changeParticipant(
                runtime,
                characterId,
                operationId,
                EncounterOperationKind.PARTICIPANT_LEFT_BOUNDARY,
                EncounterParticipantStatus.OUTSIDE_GRACE,
                Math.addExact(currentTick, REJOIN_GRACE_TICKS),
                currentTick);
    }

    public Result<BossEncounterTransition, BossEncounterErrorCode> reconnect(
            BossEncounterRuntime runtime,
            CharacterId characterId,
            UUID operationId,
            long currentTick) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(operationId, "operationId");
        requireTick(currentTick);
        Result<BossEncounterTransition, BossEncounterErrorCode> duplicate =
                duplicate(runtime, operationId, EncounterOperationKind.PARTICIPANT_RECONNECTED);
        if (duplicate != null) {
            return duplicate;
        }
        Result<EncounterParticipant, BossEncounterErrorCode> participantResult =
                requireActiveParticipant(runtime, characterId);
        if (participantResult
                instanceof Result.Failure<EncounterParticipant, BossEncounterErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        EncounterParticipant participant =
                ((Result.Success<EncounterParticipant, BossEncounterErrorCode>) participantResult)
                        .value();
        if (participant.status() != EncounterParticipantStatus.DISCONNECTED_GRACE
                && participant.status() != EncounterParticipantStatus.OUTSIDE_GRACE) {
            return Result.failure(
                    BossEncounterErrorCode.INVALID_PARTICIPANT_STATE,
                    "Only a participant in disconnect or boundary grace can rejoin.");
        }
        if (currentTick >= participant.graceDeadlineTick()) {
            return Result.failure(
                    BossEncounterErrorCode.GRACE_EXPIRED,
                    "Participant rejoin grace has expired; advance the encounter clock.");
        }
        return Result.success(
                participantTransition(
                        runtime,
                        participant.withStatus(
                                EncounterParticipantStatus.ACTIVE,
                                EncounterParticipant.NO_GRACE_DEADLINE),
                        operationId,
                        EncounterOperationKind.PARTICIPANT_RECONNECTED));
    }

    public Result<BossEncounterTransition, BossEncounterErrorCode> advanceGrace(
            BossEncounterRuntime runtime, UUID operationId, long currentTick) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(operationId, "operationId");
        requireTick(currentTick);
        Result<BossEncounterTransition, BossEncounterErrorCode> duplicate =
                duplicate(runtime, operationId, EncounterOperationKind.GRACE_ADVANCED);
        if (duplicate != null) {
            return duplicate;
        }
        if (runtime.phase() != BossEncounterPhase.ACTIVE) {
            return Result.failure(
                    BossEncounterErrorCode.INVALID_PHASE,
                    "Grace can advance only while the encounter is active.");
        }
        HashMap<CharacterId, EncounterParticipant> participants =
                new HashMap<>(runtime.participants());
        boolean expired = false;
        for (EncounterParticipant participant : runtime.participants().values()) {
            if ((participant.status() == EncounterParticipantStatus.DISCONNECTED_GRACE
                            || participant.status() == EncounterParticipantStatus.OUTSIDE_GRACE)
                    && currentTick >= participant.graceDeadlineTick()) {
                participants.put(
                        participant.characterId(),
                        participant.withStatus(
                                EncounterParticipantStatus.DEFEATED,
                                EncounterParticipant.NO_GRACE_DEADLINE));
                expired = true;
            }
        }
        if (!expired) {
            return Result.success(BossEncounterTransition.unchanged(runtime));
        }
        return Result.success(
                transition(
                        runtime,
                        phaseFor(participants),
                        runtime.attempt(),
                        participants,
                        withOperation(runtime, operationId, EncounterOperationKind.GRACE_ADVANCED),
                        Optional.empty(),
                        runtime.rewardGrantId(),
                        Set.of(),
                        false));
    }

    public Result<BossEncounterTransition, BossEncounterErrorCode> beginReset(
            BossEncounterRuntime runtime, UUID operationId) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(operationId, "operationId");
        Result<BossEncounterTransition, BossEncounterErrorCode> duplicate =
                duplicate(runtime, operationId, EncounterOperationKind.RESET_BEGUN);
        if (duplicate != null) {
            return duplicate;
        }
        if (runtime.phase() != BossEncounterPhase.WIPE_PENDING) {
            return Result.failure(
                    BossEncounterErrorCode.INVALID_PHASE,
                    "Reset can begin only after an authoritative wipe.");
        }
        return Result.success(
                transition(
                        runtime,
                        BossEncounterPhase.RESETTING,
                        runtime.attempt(),
                        runtime.participants(),
                        withOperation(runtime, operationId, EncounterOperationKind.RESET_BEGUN),
                        Optional.of(operationId),
                        Optional.empty(),
                        runtime.participants().keySet(),
                        false));
    }

    public Result<BossEncounterTransition, BossEncounterErrorCode> completeReset(
            BossEncounterRuntime runtime, UUID resetOperationId, UUID completionOperationId) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(resetOperationId, "resetOperationId");
        Objects.requireNonNull(completionOperationId, "completionOperationId");
        Result<BossEncounterTransition, BossEncounterErrorCode> duplicate =
                duplicate(runtime, completionOperationId, EncounterOperationKind.RESET_COMPLETED);
        if (duplicate != null) {
            return duplicate;
        }
        if (runtime.phase() != BossEncounterPhase.RESETTING) {
            return Result.failure(
                    BossEncounterErrorCode.INVALID_PHASE,
                    "Reset can complete only while reset effects are being committed.");
        }
        if (!runtime.activeResetOperationId().orElseThrow().equals(resetOperationId)) {
            return Result.failure(
                    BossEncounterErrorCode.RESET_OPERATION_MISMATCH,
                    "Reset completion does not match the active reset operation.");
        }
        HashMap<CharacterId, EncounterParticipant> participants = new HashMap<>();
        runtime.participants()
                .keySet()
                .forEach(
                        characterId ->
                                participants.put(
                                        characterId, EncounterParticipant.active(characterId)));
        return Result.success(
                transition(
                        runtime,
                        BossEncounterPhase.ACTIVE,
                        Math.addExact(runtime.attempt(), 1),
                        participants,
                        withOperation(
                                runtime,
                                completionOperationId,
                                EncounterOperationKind.RESET_COMPLETED),
                        Optional.empty(),
                        Optional.empty(),
                        Set.of(),
                        false));
    }

    public Result<BossEncounterTransition, BossEncounterErrorCode> confirmVictory(
            BossEncounterRuntime runtime, UUID operationId) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(operationId, "operationId");
        Result<BossEncounterTransition, BossEncounterErrorCode> duplicate =
                duplicate(runtime, operationId, EncounterOperationKind.VICTORY_CONFIRMED);
        if (duplicate != null) {
            return duplicate;
        }
        if (runtime.phase() != BossEncounterPhase.ACTIVE
                && runtime.phase() != BossEncounterPhase.WIPE_PENDING) {
            return Result.failure(
                    BossEncounterErrorCode.INVALID_PHASE,
                    "Victory cannot be confirmed after reset or reconciliation begins.");
        }
        return Result.success(
                transition(
                        runtime,
                        BossEncounterPhase.VICTORY_PENDING,
                        runtime.attempt(),
                        runtime.participants(),
                        withOperation(
                                runtime, operationId, EncounterOperationKind.VICTORY_CONFIRMED),
                        Optional.empty(),
                        Optional.empty(),
                        Set.of(),
                        true));
    }

    public Result<BossEncounterTransition, BossEncounterErrorCode> reconcileRewards(
            BossEncounterRuntime runtime, UUID operationId, UUID rewardGrantId) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(rewardGrantId, "rewardGrantId");
        Result<BossEncounterTransition, BossEncounterErrorCode> duplicate =
                duplicate(runtime, operationId, EncounterOperationKind.REWARDS_RECONCILED);
        if (duplicate != null) {
            return duplicate;
        }
        if (runtime.phase() != BossEncounterPhase.VICTORY_PENDING) {
            return Result.failure(
                    BossEncounterErrorCode.INVALID_PHASE,
                    "Rewards can reconcile only after victory is frozen.");
        }
        return Result.success(
                transition(
                        runtime,
                        BossEncounterPhase.COMPLETED,
                        runtime.attempt(),
                        runtime.participants(),
                        withOperation(
                                runtime, operationId, EncounterOperationKind.REWARDS_RECONCILED),
                        Optional.empty(),
                        Optional.of(rewardGrantId),
                        Set.of(),
                        false));
    }

    private Result<BossEncounterTransition, BossEncounterErrorCode> changeParticipant(
            BossEncounterRuntime runtime,
            CharacterId characterId,
            UUID operationId,
            EncounterOperationKind operationKind,
            EncounterParticipantStatus status,
            long deadlineTick,
            long currentTick) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(operationId, "operationId");
        requireTick(currentTick);
        Result<BossEncounterTransition, BossEncounterErrorCode> duplicate =
                duplicate(runtime, operationId, operationKind);
        if (duplicate != null) {
            return duplicate;
        }
        Result<EncounterParticipant, BossEncounterErrorCode> participantResult =
                requireActiveParticipant(runtime, characterId);
        if (participantResult
                instanceof Result.Failure<EncounterParticipant, BossEncounterErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        EncounterParticipant participant =
                ((Result.Success<EncounterParticipant, BossEncounterErrorCode>) participantResult)
                        .value();
        if (participant.status() != EncounterParticipantStatus.ACTIVE) {
            return Result.failure(
                    BossEncounterErrorCode.INVALID_PARTICIPANT_STATE,
                    "Only an active participant can become unavailable.");
        }
        return Result.success(
                participantTransition(
                        runtime,
                        participant.withStatus(status, deadlineTick),
                        operationId,
                        operationKind));
    }

    private Result<EncounterParticipant, BossEncounterErrorCode> requireActiveParticipant(
            BossEncounterRuntime runtime, CharacterId characterId) {
        if (runtime.phase() != BossEncounterPhase.ACTIVE) {
            return Result.failure(
                    BossEncounterErrorCode.INVALID_PHASE,
                    "Participant availability changes require an active encounter.");
        }
        EncounterParticipant participant = runtime.participants().get(characterId);
        if (participant == null) {
            return Result.failure(
                    BossEncounterErrorCode.PARTICIPANT_NOT_FOUND,
                    "Character is not locked to this encounter.");
        }
        return Result.success(participant);
    }

    private BossEncounterTransition participantTransition(
            BossEncounterRuntime runtime,
            EncounterParticipant replacement,
            UUID operationId,
            EncounterOperationKind operationKind) {
        HashMap<CharacterId, EncounterParticipant> participants =
                new HashMap<>(runtime.participants());
        participants.put(replacement.characterId(), replacement);
        return transition(
                runtime,
                phaseFor(participants),
                runtime.attempt(),
                participants,
                withOperation(runtime, operationId, operationKind),
                Optional.empty(),
                runtime.rewardGrantId(),
                Set.of(),
                false);
    }

    private static BossEncounterPhase phaseFor(
            Map<CharacterId, EncounterParticipant> participants) {
        boolean allDefeated =
                participants.values().stream()
                        .allMatch(
                                participant ->
                                        participant.status()
                                                == EncounterParticipantStatus.DEFEATED);
        return allDefeated ? BossEncounterPhase.WIPE_PENDING : BossEncounterPhase.ACTIVE;
    }

    private static Map<UUID, EncounterOperationKind> withOperation(
            BossEncounterRuntime runtime, UUID operationId, EncounterOperationKind operationKind) {
        HashMap<UUID, EncounterOperationKind> operations =
                new HashMap<>(runtime.processedOperations());
        operations.put(operationId, operationKind);
        return operations;
    }

    private static Result<BossEncounterTransition, BossEncounterErrorCode> duplicate(
            BossEncounterRuntime runtime, UUID operationId, EncounterOperationKind expectedKind) {
        EncounterOperationKind existing = runtime.processedOperations().get(operationId);
        if (existing == null) {
            return null;
        }
        if (existing != expectedKind) {
            return Result.failure(
                    BossEncounterErrorCode.OPERATION_ID_REUSED,
                    "Operation ID was already used for " + existing + ".");
        }
        return Result.success(BossEncounterTransition.unchanged(runtime));
    }

    private static BossEncounterTransition transition(
            BossEncounterRuntime source,
            BossEncounterPhase phase,
            int attempt,
            Map<CharacterId, EncounterParticipant> participants,
            Map<UUID, EncounterOperationKind> operations,
            Optional<UUID> activeResetOperationId,
            Optional<UUID> rewardGrantId,
            Set<CharacterId> flaskRestoreParticipants,
            boolean rewardReconciliationRequested) {
        BossEncounterRuntime runtime =
                new BossEncounterRuntime(
                        source.encounterId(),
                        source.definitionId(),
                        source.checkpointInstanceId(),
                        phase,
                        attempt,
                        source.startedTick(),
                        participants,
                        operations,
                        activeResetOperationId,
                        rewardGrantId);
        return new BossEncounterTransition(
                runtime,
                new HashSet<>(flaskRestoreParticipants),
                rewardReconciliationRequested,
                true);
    }

    private static void requireTick(long currentTick) {
        if (currentTick < 0) {
            throw new IllegalArgumentException("currentTick must not be negative");
        }
    }
}
