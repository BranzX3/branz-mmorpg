package com.branz.mmorpg.lifeskills.node;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.result.Result;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pure personal/shared node reservation, commit and wall-clock recovery state machine. */
public final class ResourceNodeEngine {
    public Result<ResourceNodeTransition, ResourceNodeErrorCode> reserve(
            ResourceNodeDefinition definition,
            ResourceNodeRuntime runtime,
            ResourceNodeReservationRequest request) {
        Objects.requireNonNull(request, "request");
        Result<ResourceNodeTransition, ResourceNodeErrorCode> valid = validate(definition, runtime);
        if (valid != null) {
            return valid;
        }
        String signature =
                request.actor().value()
                        + ":"
                        + request.toolItemId()
                        + ":"
                        + request.reservationId()
                        + ":"
                        + request.focusCost()
                        + ":"
                        + request.availableToolDurability()
                        + ":"
                        + request.regionEligible()
                        + ":"
                        + request.actionAvailable()
                        + ":"
                        + request.currentTick()
                        + ":"
                        + request.now()
                        + ":"
                        + request.toolTags().stream().sorted().toList();
        Result<ResourceNodeTransition, ResourceNodeErrorCode> replay =
                replay(
                        runtime,
                        request.operationId(),
                        ResourceNodeOperationKind.RESERVE,
                        signature);
        if (replay != null) {
            return replay;
        }
        if (!request.regionEligible() || !request.actionAvailable()) {
            return failure(
                    ResourceNodeErrorCode.ADMISSION_REJECTED,
                    "Region or action state does not allow gathering.");
        }
        if (!request.toolTags().containsAll(definition.requiredToolTags())) {
            return failure(
                    ResourceNodeErrorCode.TOOL_INVALID,
                    "Equipped tool does not satisfy the node requirements.");
        }
        if (request.availableToolDurability() < definition.durabilityCost()) {
            return failure(
                    ResourceNodeErrorCode.TOOL_DURABILITY_INSUFFICIENT,
                    "Equipped tool lacks durability for this reservation.");
        }
        ResourceNodeAccessKey key = accessKey(definition, request.actor());
        ResourceNodeSlot priorSlot = slot(definition, runtime, key);
        ResourceNodeSlot slot = normalizedSlot(definition, runtime, key, request.now());
        if (slot.phase() != ResourceNodePhase.AVAILABLE) {
            return failure(ResourceNodeErrorCode.NODE_UNAVAILABLE, "Resource node is unavailable.");
        }
        UUID yieldSeed =
                UUID.nameUUIDFromBytes(
                        (runtime.nodeId().value()
                                        + ":"
                                        + runtime.definitionId().value()
                                        + ":"
                                        + request.reservationId())
                                .getBytes(StandardCharsets.UTF_8));
        ResourceNodeReservation reservation =
                new ResourceNodeReservation(
                        request.reservationId(),
                        request.actor(),
                        request.toolItemId(),
                        yieldSeed,
                        definition.durabilityCost(),
                        request.focusCost(),
                        Math.addExact(request.currentTick(), definition.workDurationTicks()),
                        request.now(),
                        request.now().plus(definition.reservationTimeout()));
        ResourceNodeSlot reserved =
                new ResourceNodeSlot(
                        ResourceNodePhase.RESERVED,
                        slot.remainingCharges(),
                        Optional.of(reservation),
                        Optional.empty());
        ResourceNodeRuntime next =
                changedRuntime(
                        runtime,
                        key,
                        reserved,
                        request.operationId(),
                        new ResourceNodeOperation(ResourceNodeOperationKind.RESERVE, signature));
        return Result.success(
                new ResourceNodeTransition(
                        next,
                        true,
                        Optional.of(reservation),
                        Optional.empty(),
                        priorSlot.phase() == ResourceNodePhase.RESERVED
                                ? Set.of(priorSlot.reservation().orElseThrow().reservationId())
                                : Set.of(),
                        Set.of()));
    }

    public Result<ResourceNodeTransition, ResourceNodeErrorCode> cancel(
            ResourceNodeDefinition definition,
            ResourceNodeRuntime runtime,
            CharacterId actor,
            UUID reservationId,
            UUID operationId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(operationId, "operationId");
        Result<ResourceNodeTransition, ResourceNodeErrorCode> valid = validate(definition, runtime);
        if (valid != null) {
            return valid;
        }
        String signature = actor.value() + ":" + reservationId;
        Result<ResourceNodeTransition, ResourceNodeErrorCode> replay =
                replay(runtime, operationId, ResourceNodeOperationKind.CANCEL, signature);
        if (replay != null) {
            return replay;
        }
        ResourceNodeAccessKey key = accessKey(definition, actor);
        ResourceNodeSlot slot = slot(definition, runtime, key);
        ResourceNodeReservation reservation = matchingReservation(slot, actor, reservationId);
        if (reservation == null) {
            return failure(
                    ResourceNodeErrorCode.RESERVATION_INVALID,
                    "Only the reservation owner can cancel before commit.");
        }
        ResourceNodeRuntime next =
                changedRuntime(
                        runtime,
                        key,
                        ResourceNodeSlot.available(slot.remainingCharges()),
                        operationId,
                        new ResourceNodeOperation(ResourceNodeOperationKind.CANCEL, signature));
        return Result.success(
                new ResourceNodeTransition(
                        next,
                        true,
                        Optional.empty(),
                        Optional.empty(),
                        Set.of(reservationId),
                        Set.of()));
    }

    public Result<ResourceNodeTransition, ResourceNodeErrorCode> commit(
            ResourceNodeDefinition definition,
            ResourceNodeRuntime runtime,
            CharacterId actor,
            UUID reservationId,
            UUID operationId,
            long currentTick,
            Instant now) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(now, "now");
        Result<ResourceNodeTransition, ResourceNodeErrorCode> valid = validate(definition, runtime);
        if (valid != null) {
            return valid;
        }
        if (currentTick < 0) {
            throw new IllegalArgumentException("currentTick must be non-negative");
        }
        String signature = actor.value() + ":" + reservationId;
        Result<ResourceNodeTransition, ResourceNodeErrorCode> replay =
                replay(runtime, operationId, ResourceNodeOperationKind.COMMIT, signature);
        if (replay != null) {
            return replay;
        }
        ResourceNodeAccessKey key = accessKey(definition, actor);
        ResourceNodeSlot slot = slot(definition, runtime, key);
        ResourceNodeReservation reservation = matchingReservation(slot, actor, reservationId);
        if (reservation == null) {
            return failure(
                    ResourceNodeErrorCode.RESERVATION_INVALID,
                    "Harvest reservation does not match this actor and node.");
        }
        if (!now.isBefore(reservation.expiresAt())) {
            return failure(
                    ResourceNodeErrorCode.RESERVATION_EXPIRED,
                    "Harvest reservation expired before its commit point.");
        }
        if (currentTick < reservation.commitAtTick()) {
            return failure(
                    ResourceNodeErrorCode.COMMIT_TOO_EARLY,
                    "Harvest cannot commit before its authored work duration.");
        }
        int remaining = slot.remainingCharges() - 1;
        Optional<Instant> recoversAt =
                remaining == 0
                        ? Optional.of(now.plus(definition.recoveryDuration()))
                        : Optional.empty();
        ResourceNodeSlot committed =
                remaining == 0
                        ? new ResourceNodeSlot(
                                ResourceNodePhase.DEPLETED, 0, Optional.empty(), recoversAt)
                        : ResourceNodeSlot.available(remaining);
        ResourceNodeRuntime next =
                changedRuntime(
                        runtime,
                        key,
                        committed,
                        operationId,
                        new ResourceNodeOperation(ResourceNodeOperationKind.COMMIT, signature));
        ResourceNodeHarvestCommit harvest =
                new ResourceNodeHarvestCommit(
                        reservation.reservationId(),
                        actor,
                        reservation.toolItemId(),
                        reservation.yieldSeed(),
                        reservation.durabilityCost(),
                        reservation.focusCost(),
                        remaining,
                        recoversAt);
        return Result.success(
                new ResourceNodeTransition(
                        next, true, Optional.empty(), Optional.of(harvest), Set.of(), Set.of()));
    }

    public Result<ResourceNodeTransition, ResourceNodeErrorCode> beginRecovery(
            ResourceNodeDefinition definition,
            ResourceNodeRuntime runtime,
            ResourceNodeAccessKey key,
            UUID operationId) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operationId, "operationId");
        Result<ResourceNodeTransition, ResourceNodeErrorCode> valid = validate(definition, runtime);
        if (valid != null) {
            return valid;
        }
        validateAccessKey(definition, key);
        String signature = key.toString();
        Result<ResourceNodeTransition, ResourceNodeErrorCode> replay =
                replay(runtime, operationId, ResourceNodeOperationKind.BEGIN_RECOVERY, signature);
        if (replay != null) {
            return replay;
        }
        ResourceNodeSlot slot = slot(definition, runtime, key);
        if (slot.phase() != ResourceNodePhase.DEPLETED) {
            return failure(
                    ResourceNodeErrorCode.NODE_UNAVAILABLE,
                    "Only a committed depleted node can begin recovery.");
        }
        ResourceNodeSlot recovering =
                new ResourceNodeSlot(
                        ResourceNodePhase.RECOVERING, 0, Optional.empty(), slot.recoversAt());
        ResourceNodeRuntime next =
                changedRuntime(
                        runtime,
                        key,
                        recovering,
                        operationId,
                        new ResourceNodeOperation(
                                ResourceNodeOperationKind.BEGIN_RECOVERY, signature));
        return Result.success(
                new ResourceNodeTransition(
                        next, true, Optional.empty(), Optional.empty(), Set.of(), Set.of()));
    }

    /** Reconciles wall-clock timeout/recovery. Restart releases every uncommitted reservation. */
    public ResourceNodeTransition reconcile(
            ResourceNodeDefinition definition,
            ResourceNodeRuntime runtime,
            Instant now,
            boolean restart) {
        Objects.requireNonNull(now, "now");
        Result<ResourceNodeTransition, ResourceNodeErrorCode> valid = validate(definition, runtime);
        if (valid != null) {
            throw new IllegalArgumentException(
                    ((Result.Failure<ResourceNodeTransition, ResourceNodeErrorCode>) valid)
                            .detail());
        }
        HashMap<ResourceNodeAccessKey, ResourceNodeSlot> slots = new HashMap<>(runtime.slots());
        HashSet<UUID> released = new HashSet<>();
        HashSet<ResourceNodeAccessKey> recovered = new HashSet<>();
        runtime.slots()
                .forEach(
                        (key, slot) -> {
                            if (slot.phase() == ResourceNodePhase.RESERVED
                                    && (restart
                                            || !now.isBefore(
                                                    slot.reservation()
                                                            .orElseThrow()
                                                            .expiresAt()))) {
                                released.add(slot.reservation().orElseThrow().reservationId());
                                slots.put(key, ResourceNodeSlot.available(slot.remainingCharges()));
                            } else if (slot.phase() == ResourceNodePhase.DEPLETED) {
                                if (!now.isBefore(slot.recoversAt().orElseThrow())) {
                                    recovered.add(key);
                                    slots.put(
                                            key,
                                            ResourceNodeSlot.available(
                                                    definition.maximumCharges()));
                                } else {
                                    slots.put(
                                            key,
                                            new ResourceNodeSlot(
                                                    ResourceNodePhase.RECOVERING,
                                                    0,
                                                    Optional.empty(),
                                                    slot.recoversAt()));
                                }
                            } else if (slot.phase() == ResourceNodePhase.RECOVERING
                                    && !now.isBefore(slot.recoversAt().orElseThrow())) {
                                recovered.add(key);
                                slots.put(
                                        key,
                                        ResourceNodeSlot.available(definition.maximumCharges()));
                            }
                        });
        if (slots.equals(runtime.slots())) {
            return ResourceNodeTransition.unchanged(runtime);
        }
        ResourceNodeRuntime next =
                new ResourceNodeRuntime(
                        runtime.nodeId(),
                        runtime.definitionId(),
                        slots,
                        runtime.processedOperations());
        return new ResourceNodeTransition(
                next, true, Optional.empty(), Optional.empty(), released, recovered);
    }

    public ResourceNodeSlot slotFor(
            ResourceNodeDefinition definition, ResourceNodeRuntime runtime, CharacterId viewer) {
        Result<ResourceNodeTransition, ResourceNodeErrorCode> valid = validate(definition, runtime);
        if (valid != null) {
            throw new IllegalArgumentException(
                    ((Result.Failure<ResourceNodeTransition, ResourceNodeErrorCode>) valid)
                            .detail());
        }
        return slot(definition, runtime, accessKey(definition, viewer));
    }

    private static ResourceNodeSlot normalizedSlot(
            ResourceNodeDefinition definition,
            ResourceNodeRuntime runtime,
            ResourceNodeAccessKey key,
            Instant now) {
        ResourceNodeSlot slot = slot(definition, runtime, key);
        if (slot.phase() == ResourceNodePhase.RESERVED
                && !now.isBefore(slot.reservation().orElseThrow().expiresAt())) {
            return ResourceNodeSlot.available(slot.remainingCharges());
        }
        if (slot.phase() == ResourceNodePhase.RECOVERING
                && !now.isBefore(slot.recoversAt().orElseThrow())) {
            return ResourceNodeSlot.available(definition.maximumCharges());
        }
        return slot;
    }

    private static ResourceNodeReservation matchingReservation(
            ResourceNodeSlot slot, CharacterId actor, UUID reservationId) {
        if (slot.phase() != ResourceNodePhase.RESERVED) {
            return null;
        }
        ResourceNodeReservation reservation = slot.reservation().orElseThrow();
        return reservation.owner().equals(actor)
                        && reservation.reservationId().equals(reservationId)
                ? reservation
                : null;
    }

    private static ResourceNodeRuntime changedRuntime(
            ResourceNodeRuntime runtime,
            ResourceNodeAccessKey key,
            ResourceNodeSlot slot,
            UUID operationId,
            ResourceNodeOperation operation) {
        HashMap<ResourceNodeAccessKey, ResourceNodeSlot> slots = new HashMap<>(runtime.slots());
        slots.put(key, slot);
        HashMap<UUID, ResourceNodeOperation> operations =
                new HashMap<>(runtime.processedOperations());
        operations.put(operationId, operation);
        return new ResourceNodeRuntime(runtime.nodeId(), runtime.definitionId(), slots, operations);
    }

    private static Result<ResourceNodeTransition, ResourceNodeErrorCode> replay(
            ResourceNodeRuntime runtime,
            UUID operationId,
            ResourceNodeOperationKind kind,
            String signature) {
        ResourceNodeOperation previous = runtime.processedOperations().get(operationId);
        if (previous == null) {
            return null;
        }
        return previous.kind() == kind && previous.signature().equals(signature)
                ? Result.success(ResourceNodeTransition.unchanged(runtime))
                : failure(
                        ResourceNodeErrorCode.OPERATION_ID_REUSED,
                        "Resource-node operation ID was reused with different input.");
    }

    private static Result<ResourceNodeTransition, ResourceNodeErrorCode> validate(
            ResourceNodeDefinition definition, ResourceNodeRuntime runtime) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(runtime, "runtime");
        if (!definition.id().equals(runtime.definitionId())) {
            return failure(
                    ResourceNodeErrorCode.RUNTIME_INVALID,
                    "Resource-node runtime definition does not match authored content.");
        }
        try {
            runtime.slots()
                    .forEach(
                            (key, slot) -> {
                                validateAccessKey(definition, key);
                                if (slot.remainingCharges() > definition.maximumCharges()) {
                                    throw new IllegalArgumentException(
                                            "resource-node slot exceeds authored charges");
                                }
                                slot.reservation()
                                        .ifPresent(
                                                reservation -> {
                                                    if (reservation.durabilityCost()
                                                            != definition.durabilityCost()) {
                                                        throw new IllegalArgumentException(
                                                                "reservation durability drifted from content");
                                                    }
                                                    key.owner()
                                                            .ifPresent(
                                                                    owner -> {
                                                                        if (!owner.equals(
                                                                                reservation
                                                                                        .owner())) {
                                                                            throw new IllegalArgumentException(
                                                                                    "personal slot owner does not match reservation");
                                                                        }
                                                                    });
                                                });
                            });
        } catch (IllegalArgumentException exception) {
            return failure(ResourceNodeErrorCode.RUNTIME_INVALID, exception.getMessage());
        }
        return null;
    }

    private static void validateAccessKey(
            ResourceNodeDefinition definition, ResourceNodeAccessKey key) {
        boolean personal = key.owner().isPresent();
        if (personal != (definition.sharing() == ResourceNodeSharing.PERSONAL)) {
            throw new IllegalArgumentException(
                    "resource-node access key does not match sharing mode");
        }
    }

    private static ResourceNodeAccessKey accessKey(
            ResourceNodeDefinition definition, CharacterId actor) {
        Objects.requireNonNull(actor, "actor");
        return definition.sharing() == ResourceNodeSharing.PERSONAL
                ? ResourceNodeAccessKey.personal(actor)
                : ResourceNodeAccessKey.shared();
    }

    private static ResourceNodeSlot slot(
            ResourceNodeDefinition definition,
            ResourceNodeRuntime runtime,
            ResourceNodeAccessKey key) {
        return runtime.slots()
                .getOrDefault(key, ResourceNodeSlot.available(definition.maximumCharges()));
    }

    private static Result<ResourceNodeTransition, ResourceNodeErrorCode> failure(
            ResourceNodeErrorCode error, String detail) {
        return Result.failure(error, detail);
    }
}
