package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.lifeskills.node.ResourceNodeEngine;
import com.branz.mmorpg.lifeskills.node.ResourceNodeErrorCode;
import com.branz.mmorpg.lifeskills.node.ResourceNodeId;
import com.branz.mmorpg.lifeskills.node.ResourceNodeOperationKind;
import com.branz.mmorpg.lifeskills.node.ResourceNodePhase;
import com.branz.mmorpg.lifeskills.node.ResourceNodeReservation;
import com.branz.mmorpg.lifeskills.node.ResourceNodeReservationRequest;
import com.branz.mmorpg.lifeskills.node.ResourceNodeRuntime;
import com.branz.mmorpg.lifeskills.node.ResourceNodeSlot;
import com.branz.mmorpg.lifeskills.node.ResourceNodeTransition;
import com.branz.mmorpg.lifeskills.progression.LifeFocusDecision;
import com.branz.mmorpg.lifeskills.progression.LifeFocusEngine;
import com.branz.mmorpg.lifeskills.progression.LifeskillRankDecision;
import com.branz.mmorpg.lifeskills.progression.LifeskillRankEngine;
import com.branz.mmorpg.persistence.transaction.CharacterLifeskillStateMutation;
import com.branz.mmorpg.persistence.transaction.CharacterLifeskillStateRecord;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.ItemPayloadUpdate;
import com.branz.mmorpg.persistence.transaction.JdbcResourceNodeStateRepository;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.persistence.transaction.NewLotLocation;
import com.branz.mmorpg.persistence.transaction.ResourceNodeCommitKind;
import com.branz.mmorpg.persistence.transaction.ResourceNodeStateCommit;
import com.branz.mmorpg.persistence.transaction.ResourceNodeStateCommitExecution;
import com.branz.mmorpg.persistence.transaction.ResourceNodeStateRecord;
import com.branz.mmorpg.persistence.transaction.ResourceNodeStateRepository;
import com.branz.mmorpg.persistence.transaction.TransactionErrorCode;
import com.branz.mmorpg.persistence.transaction.TransactionRequest;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import com.branz.mmorpg.persistence.transaction.ValueTransactionService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Synchronous durable orchestration used from Paper async tasks. */
final class DurableResourceNodeService {
    private final ResourceNodeStateRepository nodes;
    private final ValueTransactionService values;
    private final CompiledResourceNode content;
    private final String contentVersion;
    private final int toolMaximumDurability;
    private final ResourceNodeId nodeId;
    private final ResourceNodeEngine engine = new ResourceNodeEngine();
    private final ResourceNodeStateJsonCodec nodeCodec = new ResourceNodeStateJsonCodec();
    private final ResourceNodeLifeskillStateJsonCodec lifeskillCodec =
            new ResourceNodeLifeskillStateJsonCodec();
    private final ResourceNodeToolPayloadCodec toolCodec = new ResourceNodeToolPayloadCodec();
    private final LifeskillRankEngine rankEngine;
    private final LifeFocusEngine focusEngine = new LifeFocusEngine();

    DurableResourceNodeService(
            ResourceNodeStateRepository nodes,
            ValueTransactionService values,
            CompiledResourceNode content,
            String contentVersion,
            int toolMaximumDurability) {
        this.nodes = Objects.requireNonNull(nodes, "nodes");
        this.values = Objects.requireNonNull(values, "values");
        this.content = Objects.requireNonNull(content, "content");
        rankEngine = new LifeskillRankEngine(content.rankTable());
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        if (toolMaximumDurability < 1) {
            throw new IllegalArgumentException("toolMaximumDurability must be positive");
        }
        this.toolMaximumDurability = toolMaximumDurability;
        nodeId =
                new ResourceNodeId(
                        UUID.nameUUIDFromBytes(
                                ("paper-node-lab:" + content.definition().id().value())
                                        .getBytes(StandardCharsets.UTF_8)));
    }

    CompiledResourceNode content() {
        return content;
    }

    Result<LiveResourceNodeReservation, LiveResourceNodeErrorCode> reserve(
            LoadedCharacterSession session,
            ItemLocationRecord tool,
            int focusCost,
            long currentTick,
            Instant now,
            UUID reserveOperationId,
            UUID reservationId,
            UUID commitOperationId) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(tool, "tool");
        if (!tool.definitionId().equals(content.toolDefinitionId())
                || tool.ownerCharacterId().filter(session.characterId()::equals).isEmpty()) {
            return failure(
                    LiveResourceNodeErrorCode.NODE_TOOL_INVALID,
                    "The selected durable item is not this node's authored tool.");
        }
        if (toolCodec.reservation(tool.payloadJson()).isPresent()) {
            return failure(
                    LiveResourceNodeErrorCode.NODE_TOOL_INVALID,
                    "The durable tool already belongs to another node reservation.");
        }
        Result<Optional<ResourceNodeStateRecord>, TransactionErrorCode> found = nodes.find(nodeId);
        if (found
                instanceof
                Result.Failure<Optional<ResourceNodeStateRecord>, TransactionErrorCode> failure) {
            return databaseFailure(failure);
        }
        Optional<ResourceNodeStateRecord> record =
                ((Result.Success<Optional<ResourceNodeStateRecord>, TransactionErrorCode>) found)
                        .value();
        ResourceNodeRuntime runtime =
                record.map(value -> nodeCodec.decode(value.statePayloadJson()))
                        .orElseGet(() -> ResourceNodeRuntime.initial(nodeId, content.definition()));
        ResourceNodeReservationRequest request =
                new ResourceNodeReservationRequest(
                        session.characterId(),
                        tool.itemId().value(),
                        content.definition().requiredToolTags(),
                        toolCodec.durability(tool.payloadJson(), toolMaximumDurability),
                        true,
                        true,
                        focusCost,
                        reservationId,
                        reserveOperationId,
                        currentTick,
                        now);
        Result<ResourceNodeTransition, ResourceNodeErrorCode> reserved =
                engine.reserve(content.definition(), runtime, request);
        if (reserved
                instanceof Result.Failure<ResourceNodeTransition, ResourceNodeErrorCode> failure) {
            return failure(
                    LiveResourceNodeErrorCode.NODE_UNAVAILABLE,
                    failure.error().code() + ": " + failure.detail());
        }
        ResourceNodeTransition transition =
                ((Result.Success<ResourceNodeTransition, ResourceNodeErrorCode>) reserved).value();
        ResourceNodeReservation reservation = transition.newReservation().orElseThrow();
        ItemPayloadUpdate toolUpdate =
                update(
                        tool,
                        toolCodec.reserve(
                                tool.payloadJson(), toolMaximumDurability, reservationId));
        ResourceNodeStateCommit commit =
                new ResourceNodeStateCommit(
                        ResourceNodeCommitKind.RESERVE,
                        nodeId,
                        content.definition().id(),
                        phase(transition.runtime()),
                        record.map(ResourceNodeStateRecord::version).orElse(0L),
                        nodeCodec.encode(transition.runtime()),
                        Optional.of(session.characterId()),
                        Optional.empty(),
                        Optional.of(toolUpdate),
                        List.of());
        Result<ResourceNodeStateCommitExecution, TransactionErrorCode> committed =
                nodes.commit(
                        actorRequest(session, reserveOperationId, "reserve:" + reservationId, "{}"),
                        commit);
        if (committed
                instanceof
                Result.Failure<ResourceNodeStateCommitExecution, TransactionErrorCode> failure) {
            return databaseFailure(failure);
        }
        return Result.success(
                new LiveResourceNodeReservation(
                        reservationId,
                        session.characterId(),
                        tool.itemId(),
                        reservation.commitAtTick(),
                        commitOperationId));
    }

    Result<LiveResourceNodeHarvest, LiveResourceNodeErrorCode> harvest(
            LoadedCharacterSession session,
            LiveResourceNodeReservation job,
            long currentTick,
            Instant now) {
        Result<Optional<ResourceNodeStateRecord>, TransactionErrorCode> found = nodes.find(nodeId);
        if (found
                instanceof
                Result.Failure<Optional<ResourceNodeStateRecord>, TransactionErrorCode> failure) {
            return databaseFailure(failure);
        }
        ResourceNodeStateRecord record =
                ((Result.Success<Optional<ResourceNodeStateRecord>, TransactionErrorCode>) found)
                        .value()
                        .orElse(null);
        if (record == null) {
            return failure(
                    LiveResourceNodeErrorCode.NODE_STATE_INVALID,
                    "Reserved resource-node state is missing.");
        }
        ResourceNodeRuntime runtime = nodeCodec.decode(record.statePayloadJson());
        if (runtime.processedOperations().containsKey(job.commitOperationId())) {
            return replayHarvest(session, job, record, runtime);
        }
        Result<ResourceNodeTransition, ResourceNodeErrorCode> committedTransition =
                engine.commit(
                        content.definition(),
                        runtime,
                        session.characterId(),
                        job.reservationId(),
                        job.commitOperationId(),
                        currentTick,
                        now);
        if (committedTransition
                instanceof Result.Failure<ResourceNodeTransition, ResourceNodeErrorCode> failure) {
            return failure(
                    LiveResourceNodeErrorCode.NODE_UNAVAILABLE,
                    failure.error().code() + ": " + failure.detail());
        }
        ResourceNodeTransition transition =
                ((Result.Success<ResourceNodeTransition, ResourceNodeErrorCode>)
                                committedTransition)
                        .value();
        var harvest = transition.harvestCommit().orElse(null);
        if (harvest == null) {
            return failure(
                    LiveResourceNodeErrorCode.NODE_STATE_INVALID,
                    "Harvest operation was already consumed without a live result.");
        }
        Result<Optional<ItemLocationRecord>, TransactionErrorCode> foundTool =
                values.findItem(job.toolItemId());
        if (foundTool
                instanceof
                Result.Failure<Optional<ItemLocationRecord>, TransactionErrorCode> failure) {
            return databaseFailure(failure);
        }
        ItemLocationRecord tool =
                ((Result.Success<Optional<ItemLocationRecord>, TransactionErrorCode>) foundTool)
                        .value()
                        .orElse(null);
        if (tool == null
                || toolCodec
                        .reservation(tool.payloadJson())
                        .filter(job.reservationId()::equals)
                        .isEmpty()) {
            return failure(
                    LiveResourceNodeErrorCode.NODE_TOOL_INVALID,
                    "The frozen durable tool reservation no longer matches.");
        }
        Result<Optional<CharacterLifeskillStateRecord>, TransactionErrorCode> foundState =
                nodes.findCharacterState(session.characterId());
        if (foundState
                instanceof
                Result.Failure<Optional<CharacterLifeskillStateRecord>, TransactionErrorCode>
                        failure) {
            return databaseFailure(failure);
        }
        Optional<CharacterLifeskillStateRecord> stateRecord =
                ((Result.Success<Optional<CharacterLifeskillStateRecord>, TransactionErrorCode>)
                                foundState)
                        .value();
        ResourceNodeLifeskillState prior =
                stateRecord
                        .map(value -> lifeskillCodec.decode(value.statePayloadJson()))
                        .orElseGet(
                                () ->
                                        ResourceNodeLifeskillState.initial(
                                                content.definition().discipline(), now));
        Result<LifeFocusDecision, ?> focusResult =
                focusEngine.commitWork(
                        prior.focus(), harvest.focusCost(), job.commitOperationId(), now);
        if (focusResult instanceof Result.Failure<LifeFocusDecision, ?> failure) {
            return failure(
                    LiveResourceNodeErrorCode.NODE_PROGRESSION_INVALID,
                    failure.error().code() + ": " + failure.detail());
        }
        LifeFocusDecision focus = ((Result.Success<LifeFocusDecision, ?>) focusResult).value();
        LifeskillRankDecision rank;
        Result<LifeskillRankDecision, ?> rankResult =
                rankEngine.applyCommittedEvidence(
                        prior.rank(), content.rankEvidence(), job.commitOperationId());
        if (rankResult instanceof Result.Failure<LifeskillRankDecision, ?> failure) {
            if (prior.rank().rank().ordinal()
                    != com.branz.mmorpg.lifeskills.progression.LifeskillRank.RANK_COUNT - 1) {
                return failure(
                        LiveResourceNodeErrorCode.NODE_PROGRESSION_INVALID,
                        failure.error().code() + ": " + failure.detail());
            }
            rank = new LifeskillRankDecision(prior.rank(), prior.rank().rank(), 0, false);
        } else {
            rank = ((Result.Success<LifeskillRankDecision, ?>) rankResult).value();
        }
        ResourceNodeLifeskillState nextState =
                new ResourceNodeLifeskillState(rank.runtime(), focus.runtime());
        int durabilityRemaining =
                toolCodec.durability(tool.payloadJson(), toolMaximumDurability)
                        - harvest.durabilityCost();
        UUID outputId = deterministic("node-output:" + job.commitOperationId());
        NewLotLocation output =
                new NewLotLocation(
                        new LotId(outputId),
                        content.outputDefinitionId(),
                        "node-harvest",
                        content.outputQuantity(),
                        Optional.of(session.characterId()),
                        ValueLocation.pendingRewards("node:" + job.commitOperationId()),
                        "{\"nodeId\":\""
                                + nodeId.value()
                                + "\",\"reservationId\":\""
                                + job.reservationId()
                                + "\",\"yieldSeed\":\""
                                + harvest.yieldSeed()
                                + "\"}");
        ResourceNodeStateCommit commit =
                new ResourceNodeStateCommit(
                        ResourceNodeCommitKind.HARVEST,
                        nodeId,
                        content.definition().id(),
                        phase(transition.runtime()),
                        record.version(),
                        nodeCodec.encode(transition.runtime()),
                        Optional.of(session.characterId()),
                        Optional.of(
                                new CharacterLifeskillStateMutation(
                                        session.characterId(),
                                        stateRecord
                                                .map(CharacterLifeskillStateRecord::version)
                                                .orElse(0L),
                                        lifeskillCodec.encode(nextState))),
                        Optional.of(
                                update(
                                        tool,
                                        toolCodec.spend(
                                                tool.payloadJson(),
                                                toolMaximumDurability,
                                                harvest.durabilityCost()))),
                        List.of(output));
        Result<ResourceNodeStateCommitExecution, TransactionErrorCode> persisted =
                nodes.commit(
                        actorRequest(
                                session,
                                job.commitOperationId(),
                                "harvest:" + job.reservationId(),
                                "{\"lotId\":\"" + outputId + "\"}"),
                        commit);
        if (persisted
                instanceof
                Result.Failure<ResourceNodeStateCommitExecution, TransactionErrorCode> failure) {
            return databaseFailure(failure);
        }
        return Result.success(
                new LiveResourceNodeHarvest(
                        ((Result.Success<ResourceNodeStateCommitExecution, TransactionErrorCode>)
                                        persisted)
                                .value(),
                        nextState,
                        durabilityRemaining,
                        content.outputQuantity()));
    }

    private Result<LiveResourceNodeHarvest, LiveResourceNodeErrorCode> replayHarvest(
            LoadedCharacterSession session,
            LiveResourceNodeReservation job,
            ResourceNodeStateRecord node,
            ResourceNodeRuntime runtime) {
        var operation = runtime.processedOperations().get(job.commitOperationId());
        String signature = session.characterId().value() + ":" + job.reservationId();
        if (operation.kind() != ResourceNodeOperationKind.COMMIT
                || !operation.signature().equals(signature)) {
            return failure(
                    LiveResourceNodeErrorCode.NODE_STATE_INVALID,
                    "Harvest operation ID was reused with different input.");
        }
        Result<Optional<CharacterLifeskillStateRecord>, TransactionErrorCode> foundState =
                nodes.findCharacterState(session.characterId());
        Result<Optional<ItemLocationRecord>, TransactionErrorCode> foundTool =
                values.findItem(job.toolItemId());
        UUID outputId = deterministic("node-output:" + job.commitOperationId());
        Result<Optional<LotLocationRecord>, TransactionErrorCode> foundOutput =
                values.findLot(new LotId(outputId));
        if (!(foundState
                        instanceof
                        Result.Success<
                                        Optional<CharacterLifeskillStateRecord>,
                                        TransactionErrorCode>
                                stateSuccess)
                || !(foundTool
                        instanceof
                        Result.Success<Optional<ItemLocationRecord>, TransactionErrorCode>
                                toolSuccess)
                || !(foundOutput
                        instanceof
                        Result.Success<Optional<LotLocationRecord>, TransactionErrorCode>
                                outputSuccess)
                || stateSuccess.value().isEmpty()
                || toolSuccess.value().isEmpty()
                || outputSuccess.value().isEmpty()) {
            return failure(
                    LiveResourceNodeErrorCode.NODE_DATABASE_UNAVAILABLE,
                    "Committed harvest replay values are incomplete.");
        }
        CharacterLifeskillStateRecord state = stateSuccess.value().orElseThrow();
        ItemLocationRecord tool = toolSuccess.value().orElseThrow();
        LotLocationRecord output = outputSuccess.value().orElseThrow();
        NewLotLocation replayOutput =
                new NewLotLocation(
                        output.lotId(),
                        output.definitionId(),
                        output.variant(),
                        output.quantity(),
                        output.ownerCharacterId(),
                        output.location(),
                        output.lineageJson());
        ResourceNodeStateCommit replayCommit =
                new ResourceNodeStateCommit(
                        ResourceNodeCommitKind.HARVEST,
                        nodeId,
                        content.definition().id(),
                        phase(runtime),
                        node.version(),
                        nodeCodec.encode(runtime),
                        Optional.of(session.characterId()),
                        Optional.of(
                                new CharacterLifeskillStateMutation(
                                        session.characterId(),
                                        state.version(),
                                        state.statePayloadJson())),
                        Optional.of(update(tool, tool.payloadJson())),
                        List.of(replayOutput));
        Result<ResourceNodeStateCommitExecution, TransactionErrorCode> replayed =
                nodes.commit(
                        actorRequest(
                                session,
                                job.commitOperationId(),
                                "harvest:" + job.reservationId(),
                                "{\"lotId\":\"" + outputId + "\"}"),
                        replayCommit);
        if (replayed
                instanceof
                Result.Failure<ResourceNodeStateCommitExecution, TransactionErrorCode> failure) {
            return databaseFailure(failure);
        }
        ResourceNodeStateCommitExecution execution =
                ((Result.Success<ResourceNodeStateCommitExecution, TransactionErrorCode>) replayed)
                        .value();
        return Result.success(
                new LiveResourceNodeHarvest(
                        execution,
                        lifeskillCodec.decode(state.statePayloadJson()),
                        toolCodec.durability(tool.payloadJson(), toolMaximumDurability),
                        Math.toIntExact(output.quantity())));
    }

    Result<Integer, LiveResourceNodeErrorCode> reconcile(Instant now, boolean restart) {
        Result<List<ResourceNodeStateRecord>, TransactionErrorCode> found = nodes.findRecoverable();
        if (found
                instanceof
                Result.Failure<List<ResourceNodeStateRecord>, TransactionErrorCode> failure) {
            return databaseFailure(failure);
        }
        int mutations = 0;
        for (ResourceNodeStateRecord record :
                ((Result.Success<List<ResourceNodeStateRecord>, TransactionErrorCode>) found)
                        .value()) {
            if (!record.nodeId().equals(nodeId)) {
                continue;
            }
            ResourceNodeRuntime runtime = nodeCodec.decode(record.statePayloadJson());
            Map<UUID, ResourceNodeReservation> reservations = reservations(runtime);
            ResourceNodeTransition transition =
                    engine.reconcile(content.definition(), runtime, now, restart);
            if (!transition.changed()) {
                continue;
            }
            List<UUID> released = new ArrayList<>(transition.releasedReservations());
            long expectedVersion = record.version();
            if (released.isEmpty()) {
                Result<ResourceNodeStateCommitExecution, TransactionErrorCode> persisted =
                        recoverCommit(transition.runtime(), expectedVersion, Optional.empty(), now);
                if (persisted
                        instanceof
                        Result.Failure<ResourceNodeStateCommitExecution, TransactionErrorCode>
                                failure) {
                    return databaseFailure(failure);
                }
                mutations++;
                continue;
            }
            for (UUID reservationId : released) {
                ResourceNodeReservation reservation = reservations.get(reservationId);
                Optional<ItemPayloadUpdate> toolUpdate =
                        reservation == null ? Optional.empty() : recoveryToolUpdate(reservation);
                Result<ResourceNodeStateCommitExecution, TransactionErrorCode> persisted =
                        recoverCommit(transition.runtime(), expectedVersion, toolUpdate, now);
                if (persisted
                        instanceof
                        Result.Failure<ResourceNodeStateCommitExecution, TransactionErrorCode>
                                failure) {
                    return databaseFailure(failure);
                }
                expectedVersion =
                        ((Result.Success<ResourceNodeStateCommitExecution, TransactionErrorCode>)
                                        persisted)
                                .value()
                                .node()
                                .version();
                mutations++;
            }
        }
        return Result.success(mutations);
    }

    Result<Optional<ResourceNodeStateRecord>, LiveResourceNodeErrorCode> findNode() {
        Result<Optional<ResourceNodeStateRecord>, TransactionErrorCode> found = nodes.find(nodeId);
        if (found
                instanceof
                Result.Failure<Optional<ResourceNodeStateRecord>, TransactionErrorCode> failure) {
            return databaseFailure(failure);
        }
        return Result.success(
                ((Result.Success<Optional<ResourceNodeStateRecord>, TransactionErrorCode>) found)
                        .value());
    }

    Result<Optional<CharacterLifeskillStateRecord>, LiveResourceNodeErrorCode> findCharacter(
            CharacterId characterId) {
        Result<Optional<CharacterLifeskillStateRecord>, TransactionErrorCode> found =
                nodes.findCharacterState(characterId);
        if (found
                instanceof
                Result.Failure<Optional<CharacterLifeskillStateRecord>, TransactionErrorCode>
                        failure) {
            return databaseFailure(failure);
        }
        return Result.success(
                ((Result.Success<Optional<CharacterLifeskillStateRecord>, TransactionErrorCode>)
                                found)
                        .value());
    }

    ResourceNodeRuntime decode(ResourceNodeStateRecord record) {
        return nodeCodec.decode(record.statePayloadJson());
    }

    ResourceNodeLifeskillState decode(CharacterLifeskillStateRecord record) {
        return lifeskillCodec.decode(record.statePayloadJson());
    }

    int toolDurability(ItemLocationRecord tool) {
        return toolCodec.durability(tool.payloadJson(), toolMaximumDurability);
    }

    private Result<ResourceNodeStateCommitExecution, TransactionErrorCode> recoverCommit(
            ResourceNodeRuntime runtime,
            long expectedVersion,
            Optional<ItemPayloadUpdate> toolUpdate,
            Instant now) {
        UUID operationId = UUID.randomUUID();
        return nodes.commit(
                TransactionRequest.system(
                        new TransactionId(operationId),
                        "node-recover:" + nodeId.value() + ":" + operationId,
                        JdbcResourceNodeStateRepository.RESOURCE_NODE_STATE_COMMIT,
                        "{\"restart\":" + false + ",\"at\":\"" + now + "\"}",
                        "{}",
                        contentVersion),
                new ResourceNodeStateCommit(
                        ResourceNodeCommitKind.RECOVER,
                        nodeId,
                        content.definition().id(),
                        phase(runtime),
                        expectedVersion,
                        nodeCodec.encode(runtime),
                        Optional.empty(),
                        Optional.empty(),
                        toolUpdate,
                        List.of()));
    }

    private Optional<ItemPayloadUpdate> recoveryToolUpdate(ResourceNodeReservation reservation) {
        Result<Optional<ItemLocationRecord>, TransactionErrorCode> found =
                values.findItem(new ItemId(reservation.toolItemId()));
        if (!(found
                instanceof
                Result.Success<Optional<ItemLocationRecord>, TransactionErrorCode> success)) {
            return Optional.empty();
        }
        return success.value()
                .filter(
                        tool ->
                                toolCodec
                                        .reservation(tool.payloadJson())
                                        .filter(reservation.reservationId()::equals)
                                        .isPresent())
                .map(
                        tool ->
                                update(
                                        tool,
                                        toolCodec.release(
                                                tool.payloadJson(), toolMaximumDurability)));
    }

    private TransactionRequest actorRequest(
            LoadedCharacterSession session,
            UUID operationId,
            String reservedInputs,
            String outputs) {
        return TransactionRequest.forCharacter(
                new TransactionId(operationId),
                "node:" + operationId,
                session.characterId(),
                session.sessionId(),
                JdbcResourceNodeStateRepository.RESOURCE_NODE_STATE_COMMIT,
                "{\"operation\":\"" + reservedInputs + "\"}",
                outputs,
                contentVersion);
    }

    private static ItemPayloadUpdate update(ItemLocationRecord tool, String replacement) {
        return new ItemPayloadUpdate(
                tool.itemId(),
                tool.version(),
                tool.ownerCharacterId(),
                tool.location(),
                tool.payloadJson(),
                replacement);
    }

    private static Map<UUID, ResourceNodeReservation> reservations(ResourceNodeRuntime runtime) {
        java.util.LinkedHashMap<UUID, ResourceNodeReservation> reservations =
                new java.util.LinkedHashMap<>();
        runtime.slots().values().stream()
                .map(ResourceNodeSlot::reservation)
                .flatMap(Optional::stream)
                .forEach(value -> reservations.put(value.reservationId(), value));
        return Map.copyOf(reservations);
    }

    private static String phase(ResourceNodeRuntime runtime) {
        if (runtime.slots().values().stream()
                .anyMatch(slot -> slot.phase() == ResourceNodePhase.RESERVED)) {
            return ResourceNodePhase.RESERVED.name();
        }
        if (runtime.slots().values().stream()
                .anyMatch(slot -> slot.phase() == ResourceNodePhase.DEPLETED)) {
            return ResourceNodePhase.DEPLETED.name();
        }
        if (runtime.slots().values().stream()
                .anyMatch(slot -> slot.phase() == ResourceNodePhase.RECOVERING)) {
            return ResourceNodePhase.RECOVERING.name();
        }
        return ResourceNodePhase.AVAILABLE.name();
    }

    private static UUID deterministic(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static <T> Result<T, LiveResourceNodeErrorCode> databaseFailure(
            Result.Failure<?, TransactionErrorCode> failure) {
        return failure(
                LiveResourceNodeErrorCode.NODE_DATABASE_UNAVAILABLE,
                failure.error().code() + ": " + failure.detail());
    }

    private static <T> Result<T, LiveResourceNodeErrorCode> failure(
            LiveResourceNodeErrorCode error, String detail) {
        return Result.failure(error, detail);
    }
}
