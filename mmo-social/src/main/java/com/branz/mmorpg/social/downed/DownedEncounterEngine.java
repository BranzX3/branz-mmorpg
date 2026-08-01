package com.branz.mmorpg.social.downed;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Pure party-PvE downed and revive state machine. */
public final class DownedEncounterEngine {
    public static final int MAX_PARTICIPANTS = 5;
    public static final long DOWNED_DURATION_TICKS = 300;
    public static final long REVIVE_CHANNEL_TICKS = 80;
    public static final long REVIVE_PROTECTION_TICKS = 60;
    public static final double REVIVED_HEALTH_RATIO = 0.25;

    public Result<DownedEncounterRuntime, DownedErrorCode> start(
            EncounterId encounterId, Collection<CharacterId> participantIds) {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(participantIds, "participantIds");
        Set<CharacterId> unique = Set.copyOf(participantIds);
        if (unique.isEmpty()
                || unique.size() > MAX_PARTICIPANTS
                || unique.size() != participantIds.size()) {
            return Result.failure(
                    DownedErrorCode.INVALID_PARTICIPANTS,
                    "Downed encounter requires one to five unique participants.");
        }
        HashMap<CharacterId, DownedParticipant> participants = new HashMap<>();
        unique.forEach(id -> participants.put(id, DownedParticipant.active(id)));
        return Result.success(
                new DownedEncounterRuntime(encounterId, participants, Map.of(), Map.of()));
    }

    public Result<DownedTransition, DownedErrorCode> lethalDamage(
            DownedEncounterRuntime runtime,
            CharacterId targetId,
            boolean execute,
            UUID operationId,
            long currentTick) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(operationId, "operationId");
        requireTick(currentTick);
        Result<DownedTransition, DownedErrorCode> duplicate =
                duplicate(runtime, operationId, DownedOperationKind.LETHAL_DAMAGE);
        if (duplicate != null) {
            return duplicate;
        }
        DownedParticipant target = runtime.participants().get(targetId);
        if (target == null) {
            return Result.failure(
                    DownedErrorCode.PARTICIPANT_NOT_FOUND,
                    "Lethal target is not an encounter participant.");
        }
        if (target.lifeState() == EncounterLifeState.DEAD) {
            return Result.failure(
                    DownedErrorCode.INVALID_LIFE_STATE,
                    "Dead participant cannot receive another lethal transition.");
        }
        boolean dies =
                execute
                        || runtime.participants().size() == 1
                        || target.reviveConsumed()
                        || target.lifeState() == EncounterLifeState.DOWNED;
        DownedParticipant replacement =
                dies
                        ? new DownedParticipant(
                                targetId,
                                EncounterLifeState.DEAD,
                                target.reviveConsumed(),
                                DownedParticipant.NO_DEADLINE,
                                DownedParticipant.NO_DEADLINE)
                        : new DownedParticipant(
                                targetId,
                                EncounterLifeState.DOWNED,
                                false,
                                Math.addExact(currentTick, DOWNED_DURATION_TICKS),
                                DownedParticipant.NO_DEADLINE);
        HashMap<CharacterId, DownedParticipant> participants =
                new HashMap<>(runtime.participants());
        participants.put(targetId, replacement);
        Map<CharacterId, ReviveChannel> channels =
                withoutParticipant(runtime.reviveChannelsByTarget(), targetId);
        return Result.success(
                transition(
                        runtime,
                        participants,
                        channels,
                        operationId,
                        DownedOperationKind.LETHAL_DAMAGE,
                        dies ? Set.of() : Set.of(targetId),
                        dies ? Set.of(targetId) : Set.of(),
                        Map.of()));
    }

    public Result<DownedTransition, DownedErrorCode> beginRevive(
            DownedEncounterRuntime runtime,
            CharacterId reviverId,
            CharacterId targetId,
            UUID channelId,
            UUID operationId,
            long currentTick) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(reviverId, "reviverId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(operationId, "operationId");
        requireTick(currentTick);
        Result<DownedTransition, DownedErrorCode> duplicate =
                duplicate(runtime, operationId, DownedOperationKind.REVIVE_BEGUN);
        if (duplicate != null) {
            return duplicate;
        }
        DownedParticipant reviver = runtime.participants().get(reviverId);
        DownedParticipant target = runtime.participants().get(targetId);
        if (reviver == null || target == null) {
            return Result.failure(
                    DownedErrorCode.PARTICIPANT_NOT_FOUND,
                    "Revive requires two encounter participants.");
        }
        if (reviver.lifeState() != EncounterLifeState.ACTIVE
                || target.lifeState() != EncounterLifeState.DOWNED) {
            return Result.failure(
                    DownedErrorCode.INVALID_LIFE_STATE,
                    "Reviver must be active and target must be downed.");
        }
        if (currentTick >= target.downedDeadlineTick()) {
            return Result.failure(
                    DownedErrorCode.REVIVE_TARGET_EXPIRED,
                    "Downed duration expired before revive began.");
        }
        boolean busy =
                runtime.reviveChannelsByTarget().containsKey(targetId)
                        || runtime.reviveChannelsByTarget().values().stream()
                                .anyMatch(channel -> channel.reviverId().equals(reviverId));
        if (busy) {
            return Result.failure(
                    DownedErrorCode.REVIVE_CHANNEL_BUSY,
                    "Reviver or target already owns a revive channel.");
        }
        HashMap<CharacterId, ReviveChannel> channels =
                new HashMap<>(runtime.reviveChannelsByTarget());
        channels.put(
                targetId,
                new ReviveChannel(
                        channelId,
                        reviverId,
                        targetId,
                        currentTick,
                        Math.addExact(currentTick, REVIVE_CHANNEL_TICKS)));
        return Result.success(
                transition(
                        runtime,
                        runtime.participants(),
                        channels,
                        operationId,
                        DownedOperationKind.REVIVE_BEGUN,
                        Set.of(),
                        Set.of(),
                        Map.of()));
    }

    public Result<DownedTransition, DownedErrorCode> interruptRevive(
            DownedEncounterRuntime runtime, CharacterId targetId, UUID operationId) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(operationId, "operationId");
        Result<DownedTransition, DownedErrorCode> duplicate =
                duplicate(runtime, operationId, DownedOperationKind.REVIVE_INTERRUPTED);
        if (duplicate != null) {
            return duplicate;
        }
        if (!runtime.reviveChannelsByTarget().containsKey(targetId)) {
            return Result.failure(
                    DownedErrorCode.REVIVE_CHANNEL_NOT_FOUND,
                    "Target has no active revive channel.");
        }
        HashMap<CharacterId, ReviveChannel> channels =
                new HashMap<>(runtime.reviveChannelsByTarget());
        channels.remove(targetId);
        return Result.success(
                transition(
                        runtime,
                        runtime.participants(),
                        channels,
                        operationId,
                        DownedOperationKind.REVIVE_INTERRUPTED,
                        Set.of(),
                        Set.of(),
                        Map.of()));
    }

    public Result<DownedTransition, DownedErrorCode> hostileAction(
            DownedEncounterRuntime runtime,
            CharacterId actorId,
            UUID operationId,
            long currentTick) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(operationId, "operationId");
        requireTick(currentTick);
        Result<DownedTransition, DownedErrorCode> duplicate =
                duplicate(runtime, operationId, DownedOperationKind.HOSTILE_ACTION);
        if (duplicate != null) {
            return duplicate;
        }
        DownedParticipant actor = runtime.participants().get(actorId);
        if (actor == null) {
            return Result.failure(
                    DownedErrorCode.PARTICIPANT_NOT_FOUND,
                    "Hostile actor is not an encounter participant.");
        }
        if (!actor.protectedAt(currentTick)) {
            return Result.success(DownedTransition.unchanged(runtime));
        }
        HashMap<CharacterId, DownedParticipant> participants =
                new HashMap<>(runtime.participants());
        participants.put(
                actorId,
                new DownedParticipant(
                        actorId,
                        actor.lifeState(),
                        actor.reviveConsumed(),
                        actor.downedDeadlineTick(),
                        DownedParticipant.NO_DEADLINE));
        return Result.success(
                transition(
                        runtime,
                        participants,
                        runtime.reviveChannelsByTarget(),
                        operationId,
                        DownedOperationKind.HOSTILE_ACTION,
                        Set.of(),
                        Set.of(),
                        Map.of()));
    }

    public Result<DownedTransition, DownedErrorCode> advance(
            DownedEncounterRuntime runtime, UUID operationId, long currentTick) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(operationId, "operationId");
        requireTick(currentTick);
        Result<DownedTransition, DownedErrorCode> duplicate =
                duplicate(runtime, operationId, DownedOperationKind.CLOCK_ADVANCED);
        if (duplicate != null) {
            return duplicate;
        }
        HashMap<CharacterId, DownedParticipant> participants =
                new HashMap<>(runtime.participants());
        HashSet<CharacterId> dead = new HashSet<>();
        for (DownedParticipant participant : runtime.participants().values()) {
            if (participant.lifeState() == EncounterLifeState.DOWNED
                    && currentTick >= participant.downedDeadlineTick()) {
                participants.put(
                        participant.characterId(),
                        new DownedParticipant(
                                participant.characterId(),
                                EncounterLifeState.DEAD,
                                participant.reviveConsumed(),
                                DownedParticipant.NO_DEADLINE,
                                DownedParticipant.NO_DEADLINE));
                dead.add(participant.characterId());
            } else if (participant.lifeState() == EncounterLifeState.ACTIVE
                    && participant.protectionUntilTick() >= 0
                    && currentTick >= participant.protectionUntilTick()) {
                participants.put(
                        participant.characterId(),
                        new DownedParticipant(
                                participant.characterId(),
                                EncounterLifeState.ACTIVE,
                                participant.reviveConsumed(),
                                DownedParticipant.NO_DEADLINE,
                                DownedParticipant.NO_DEADLINE));
            }
        }
        HashMap<CharacterId, ReviveChannel> channels =
                new HashMap<>(runtime.reviveChannelsByTarget());
        channels.entrySet()
                .removeIf(
                        entry ->
                                dead.contains(entry.getKey())
                                        || dead.contains(entry.getValue().reviverId()));
        HashMap<CharacterId, Double> revived = new HashMap<>();
        for (ReviveChannel channel : Map.copyOf(channels).values()) {
            if (currentTick < channel.commitTick()) {
                continue;
            }
            DownedParticipant target = participants.get(channel.targetId());
            DownedParticipant reviver = participants.get(channel.reviverId());
            if (target.lifeState() == EncounterLifeState.DOWNED
                    && reviver.lifeState() == EncounterLifeState.ACTIVE) {
                participants.put(
                        channel.targetId(),
                        new DownedParticipant(
                                channel.targetId(),
                                EncounterLifeState.ACTIVE,
                                true,
                                DownedParticipant.NO_DEADLINE,
                                Math.addExact(currentTick, REVIVE_PROTECTION_TICKS)));
                revived.put(channel.targetId(), REVIVED_HEALTH_RATIO);
            }
            channels.remove(channel.targetId());
        }
        boolean changed =
                !participants.equals(runtime.participants())
                        || !channels.equals(runtime.reviveChannelsByTarget());
        if (!changed) {
            return Result.success(DownedTransition.unchanged(runtime));
        }
        return Result.success(
                transition(
                        runtime,
                        participants,
                        channels,
                        operationId,
                        DownedOperationKind.CLOCK_ADVANCED,
                        Set.of(),
                        dead,
                        revived));
    }

    private static DownedTransition transition(
            DownedEncounterRuntime source,
            Map<CharacterId, DownedParticipant> participants,
            Map<CharacterId, ReviveChannel> channels,
            UUID operationId,
            DownedOperationKind operationKind,
            Set<CharacterId> downed,
            Set<CharacterId> dead,
            Map<CharacterId, Double> revived) {
        HashMap<UUID, DownedOperationKind> operations = new HashMap<>(source.processedOperations());
        operations.put(operationId, operationKind);
        return new DownedTransition(
                new DownedEncounterRuntime(
                        source.encounterId(), participants, channels, operations),
                downed,
                dead,
                revived,
                true);
    }

    private static Map<CharacterId, ReviveChannel> withoutParticipant(
            Map<CharacterId, ReviveChannel> source, CharacterId participantId) {
        HashMap<CharacterId, ReviveChannel> channels = new HashMap<>(source);
        channels.entrySet()
                .removeIf(
                        entry ->
                                entry.getKey().equals(participantId)
                                        || entry.getValue().reviverId().equals(participantId));
        return channels;
    }

    private static Result<DownedTransition, DownedErrorCode> duplicate(
            DownedEncounterRuntime runtime, UUID operationId, DownedOperationKind expectedKind) {
        DownedOperationKind existing = runtime.processedOperations().get(operationId);
        if (existing == null) {
            return null;
        }
        if (existing != expectedKind) {
            return Result.failure(
                    DownedErrorCode.OPERATION_ID_REUSED,
                    "Operation ID was already used for " + existing + ".");
        }
        return Result.success(DownedTransition.unchanged(runtime));
    }

    private static void requireTick(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("currentTick must not be negative");
        }
    }
}
