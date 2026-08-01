package com.branz.mmorpg.social.party;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.PartyId;
import com.branz.mmorpg.api.result.Result;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pure party membership, invitation, reconnect-grace and ready-check state machine. */
public final class PartyEngine {
    public static final int MAX_MEMBERS = 5;
    public static final long INVITATION_DURATION_TICKS = 1200;
    public static final long DISCONNECT_GRACE_TICKS = 6000;
    public static final long READY_CHECK_DURATION_TICKS = 600;

    public PartyRuntime start(PartyId partyId, CharacterId leaderId) {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(leaderId, "leaderId");
        return new PartyRuntime(
                partyId,
                Optional.of(leaderId),
                Map.of(leaderId, PartyMember.online(leaderId, 0)),
                Map.of(),
                Optional.empty(),
                1,
                false,
                Map.of());
    }

    public Result<PartyTransition, PartyErrorCode> invite(
            PartyRuntime runtime,
            CharacterId actorId,
            CharacterId targetId,
            UUID operationId,
            long currentTick) {
        Result<PartyTransition, PartyErrorCode> preflight =
                preflight(runtime, operationId, PartyOperationKind.INVITE);
        if (preflight != null) {
            return preflight;
        }
        requireTick(currentTick);
        Result<PartyTransition, PartyErrorCode> leader = requireLeader(runtime, actorId);
        if (leader != null) {
            return leader;
        }
        if (runtime.members().containsKey(targetId)) {
            return failure(PartyErrorCode.ALREADY_MEMBER, "Target is already a party member.");
        }
        HashMap<CharacterId, PartyInvitation> invitations = new HashMap<>(runtime.invitations());
        invitations.put(
                targetId,
                new PartyInvitation(
                        targetId,
                        actorId,
                        currentTick,
                        Math.addExact(currentTick, INVITATION_DURATION_TICKS)));
        return changed(
                runtime,
                runtime.leaderId(),
                runtime.members(),
                invitations,
                runtime.readyCheck(),
                runtime.nextJoinedOrder(),
                false,
                operationId,
                PartyOperationKind.INVITE,
                Set.of(),
                Set.of(),
                Optional.empty(),
                Optional.empty());
    }

    public Result<PartyTransition, PartyErrorCode> accept(
            PartyRuntime runtime, CharacterId targetId, UUID operationId, long currentTick) {
        Result<PartyTransition, PartyErrorCode> preflight =
                preflight(runtime, operationId, PartyOperationKind.ACCEPT);
        if (preflight != null) {
            return preflight;
        }
        requireTick(currentTick);
        if (runtime.members().containsKey(targetId)) {
            return failure(PartyErrorCode.ALREADY_MEMBER, "Target is already a party member.");
        }
        PartyInvitation invitation = runtime.invitations().get(targetId);
        if (invitation == null) {
            return failure(
                    PartyErrorCode.INVITATION_NOT_FOUND, "No party invitation exists for target.");
        }
        if (currentTick >= invitation.expiresTick()) {
            return failure(PartyErrorCode.INVITATION_EXPIRED, "Party invitation has expired.");
        }
        if (runtime.members().size() >= MAX_MEMBERS) {
            return failure(PartyErrorCode.PARTY_FULL, "Party already has five members.");
        }
        HashMap<CharacterId, PartyMember> members = new HashMap<>(runtime.members());
        members.put(targetId, PartyMember.online(targetId, runtime.nextJoinedOrder()));
        HashMap<CharacterId, PartyInvitation> invitations = new HashMap<>(runtime.invitations());
        invitations.remove(targetId);
        return changed(
                runtime,
                runtime.leaderId(),
                members,
                invitations,
                Optional.empty(),
                runtime.nextJoinedOrder() + 1,
                false,
                operationId,
                PartyOperationKind.ACCEPT,
                Set.of(targetId),
                Set.of(),
                Optional.empty(),
                Optional.empty());
    }

    public Result<PartyTransition, PartyErrorCode> decline(
            PartyRuntime runtime, CharacterId targetId, UUID operationId, long currentTick) {
        Result<PartyTransition, PartyErrorCode> preflight =
                preflight(runtime, operationId, PartyOperationKind.DECLINE);
        if (preflight != null) {
            return preflight;
        }
        requireTick(currentTick);
        PartyInvitation invitation = runtime.invitations().get(targetId);
        if (invitation == null) {
            return failure(
                    PartyErrorCode.INVITATION_NOT_FOUND, "No party invitation exists for target.");
        }
        if (currentTick >= invitation.expiresTick()) {
            return failure(PartyErrorCode.INVITATION_EXPIRED, "Party invitation has expired.");
        }
        HashMap<CharacterId, PartyInvitation> invitations = new HashMap<>(runtime.invitations());
        invitations.remove(targetId);
        return changed(
                runtime,
                runtime.leaderId(),
                runtime.members(),
                invitations,
                runtime.readyCheck(),
                runtime.nextJoinedOrder(),
                false,
                operationId,
                PartyOperationKind.DECLINE,
                Set.of(),
                Set.of(),
                Optional.empty(),
                Optional.empty());
    }

    public Result<PartyTransition, PartyErrorCode> transferLeader(
            PartyRuntime runtime, CharacterId actorId, CharacterId targetId, UUID operationId) {
        Result<PartyTransition, PartyErrorCode> preflight =
                preflight(runtime, operationId, PartyOperationKind.TRANSFER_LEADER);
        if (preflight != null) {
            return preflight;
        }
        Result<PartyTransition, PartyErrorCode> leader = requireLeader(runtime, actorId);
        if (leader != null) {
            return leader;
        }
        if (!runtime.members().containsKey(targetId)) {
            return failure(PartyErrorCode.MEMBER_NOT_FOUND, "New leader is not a party member.");
        }
        if (targetId.equals(actorId)) {
            return Result.success(PartyTransition.unchanged(runtime));
        }
        return changed(
                runtime,
                Optional.of(targetId),
                runtime.members(),
                runtime.invitations(),
                runtime.readyCheck(),
                runtime.nextJoinedOrder(),
                false,
                operationId,
                PartyOperationKind.TRANSFER_LEADER,
                Set.of(),
                Set.of(),
                Optional.of(targetId),
                Optional.empty());
    }

    public Result<PartyTransition, PartyErrorCode> kick(
            PartyRuntime runtime, CharacterId actorId, CharacterId targetId, UUID operationId) {
        Result<PartyTransition, PartyErrorCode> preflight =
                preflight(runtime, operationId, PartyOperationKind.KICK);
        if (preflight != null) {
            return preflight;
        }
        Result<PartyTransition, PartyErrorCode> leader = requireLeader(runtime, actorId);
        if (leader != null) {
            return leader;
        }
        if (actorId.equals(targetId) || !runtime.members().containsKey(targetId)) {
            return failure(PartyErrorCode.MEMBER_NOT_FOUND, "Kick target is not eligible.");
        }
        return removeMember(runtime, targetId, operationId, PartyOperationKind.KICK);
    }

    public Result<PartyTransition, PartyErrorCode> leave(
            PartyRuntime runtime, CharacterId actorId, UUID operationId) {
        Result<PartyTransition, PartyErrorCode> preflight =
                preflight(runtime, operationId, PartyOperationKind.LEAVE);
        if (preflight != null) {
            return preflight;
        }
        if (!runtime.members().containsKey(actorId)) {
            return failure(PartyErrorCode.MEMBER_NOT_FOUND, "Leaver is not a party member.");
        }
        return removeMember(runtime, actorId, operationId, PartyOperationKind.LEAVE);
    }

    public Result<PartyTransition, PartyErrorCode> disconnect(
            PartyRuntime runtime, CharacterId memberId, UUID operationId, long currentTick) {
        Result<PartyTransition, PartyErrorCode> preflight =
                preflight(runtime, operationId, PartyOperationKind.DISCONNECT);
        if (preflight != null) {
            return preflight;
        }
        requireTick(currentTick);
        PartyMember member = runtime.members().get(memberId);
        if (member == null) {
            return failure(
                    PartyErrorCode.MEMBER_NOT_FOUND, "Disconnecting player is not a member.");
        }
        if (member.status() == PartyMemberStatus.DISCONNECTED_GRACE) {
            return Result.success(PartyTransition.unchanged(runtime));
        }
        HashMap<CharacterId, PartyMember> members = new HashMap<>(runtime.members());
        members.put(
                memberId,
                new PartyMember(
                        memberId,
                        PartyMemberStatus.DISCONNECTED_GRACE,
                        member.joinedOrder(),
                        Math.addExact(currentTick, DISCONNECT_GRACE_TICKS)));
        return changed(
                runtime,
                runtime.leaderId(),
                members,
                runtime.invitations(),
                Optional.empty(),
                runtime.nextJoinedOrder(),
                false,
                operationId,
                PartyOperationKind.DISCONNECT,
                Set.of(),
                Set.of(),
                Optional.empty(),
                Optional.empty());
    }

    public Result<PartyTransition, PartyErrorCode> reconnect(
            PartyRuntime runtime, CharacterId memberId, UUID operationId, long currentTick) {
        Result<PartyTransition, PartyErrorCode> preflight =
                preflight(runtime, operationId, PartyOperationKind.RECONNECT);
        if (preflight != null) {
            return preflight;
        }
        requireTick(currentTick);
        PartyMember member = runtime.members().get(memberId);
        if (member == null) {
            return failure(PartyErrorCode.MEMBER_NOT_FOUND, "Reconnecting player is not a member.");
        }
        if (member.status() == PartyMemberStatus.ONLINE) {
            return Result.success(PartyTransition.unchanged(runtime));
        }
        if (currentTick >= member.disconnectDeadlineTick()) {
            return failure(
                    PartyErrorCode.MEMBER_NOT_FOUND, "Disconnect grace expired before reconnect.");
        }
        HashMap<CharacterId, PartyMember> members = new HashMap<>(runtime.members());
        members.put(memberId, PartyMember.online(memberId, member.joinedOrder()));
        return changed(
                runtime,
                runtime.leaderId(),
                members,
                runtime.invitations(),
                runtime.readyCheck(),
                runtime.nextJoinedOrder(),
                false,
                operationId,
                PartyOperationKind.RECONNECT,
                Set.of(),
                Set.of(),
                Optional.empty(),
                Optional.empty());
    }

    public Result<PartyTransition, PartyErrorCode> startReadyCheck(
            PartyRuntime runtime,
            CharacterId actorId,
            UUID checkId,
            UUID operationId,
            long currentTick) {
        Result<PartyTransition, PartyErrorCode> preflight =
                preflight(runtime, operationId, PartyOperationKind.READY_CHECK_STARTED);
        if (preflight != null) {
            return preflight;
        }
        requireTick(currentTick);
        Result<PartyTransition, PartyErrorCode> leader = requireLeader(runtime, actorId);
        if (leader != null) {
            return leader;
        }
        if (runtime.readyCheck().isPresent()) {
            return failure(PartyErrorCode.READY_CHECK_ACTIVE, "A ready check is already active.");
        }
        boolean complete = runtime.members().size() == 1;
        return changed(
                runtime,
                runtime.leaderId(),
                runtime.members(),
                runtime.invitations(),
                complete
                        ? Optional.empty()
                        : Optional.of(
                                new PartyReadyCheck(
                                        checkId,
                                        actorId,
                                        currentTick,
                                        Math.addExact(currentTick, READY_CHECK_DURATION_TICKS),
                                        Map.of(actorId, true))),
                runtime.nextJoinedOrder(),
                false,
                operationId,
                PartyOperationKind.READY_CHECK_STARTED,
                Set.of(),
                Set.of(),
                Optional.empty(),
                complete ? Optional.of(true) : Optional.empty());
    }

    public Result<PartyTransition, PartyErrorCode> respondReady(
            PartyRuntime runtime,
            CharacterId memberId,
            boolean ready,
            UUID operationId,
            long currentTick) {
        Result<PartyTransition, PartyErrorCode> preflight =
                preflight(runtime, operationId, PartyOperationKind.READY_CHECK_RESPONDED);
        if (preflight != null) {
            return preflight;
        }
        requireTick(currentTick);
        PartyReadyCheck check = runtime.readyCheck().orElse(null);
        if (check == null) {
            return failure(PartyErrorCode.READY_CHECK_NOT_FOUND, "No ready check is active.");
        }
        if (currentTick >= check.expiresTick()) {
            return failure(PartyErrorCode.READY_CHECK_EXPIRED, "Ready check has expired.");
        }
        if (!runtime.members().containsKey(memberId)) {
            return failure(PartyErrorCode.MEMBER_NOT_FOUND, "Responder is not a party member.");
        }
        HashMap<CharacterId, Boolean> responses = new HashMap<>(check.responses());
        responses.put(memberId, ready);
        boolean complete = responses.size() == runtime.members().size();
        boolean allReady = complete && responses.values().stream().allMatch(Boolean::booleanValue);
        return changed(
                runtime,
                runtime.leaderId(),
                runtime.members(),
                runtime.invitations(),
                complete
                        ? Optional.empty()
                        : Optional.of(
                                new PartyReadyCheck(
                                        check.checkId(),
                                        check.startedBy(),
                                        check.startedTick(),
                                        check.expiresTick(),
                                        responses)),
                runtime.nextJoinedOrder(),
                false,
                operationId,
                PartyOperationKind.READY_CHECK_RESPONDED,
                Set.of(),
                Set.of(),
                Optional.empty(),
                complete ? Optional.of(allReady) : Optional.empty());
    }

    public Result<PartyTransition, PartyErrorCode> advance(
            PartyRuntime runtime, UUID operationId, long currentTick) {
        Result<PartyTransition, PartyErrorCode> preflight =
                preflight(runtime, operationId, PartyOperationKind.CLOCK_ADVANCED);
        if (preflight != null) {
            return preflight;
        }
        requireTick(currentTick);
        HashMap<CharacterId, PartyInvitation> invitations = new HashMap<>(runtime.invitations());
        invitations.values().removeIf(invitation -> currentTick >= invitation.expiresTick());
        HashMap<CharacterId, PartyMember> members = new HashMap<>(runtime.members());
        HashSet<CharacterId> removed = new HashSet<>();
        members.values()
                .removeIf(
                        member -> {
                            boolean expired =
                                    member.status() == PartyMemberStatus.DISCONNECTED_GRACE
                                            && currentTick >= member.disconnectDeadlineTick();
                            if (expired) {
                                removed.add(member.characterId());
                            }
                            return expired;
                        });
        Optional<Boolean> readyResult = Optional.empty();
        Optional<PartyReadyCheck> readyCheck = runtime.readyCheck();
        if (readyCheck.isPresent() && currentTick >= readyCheck.orElseThrow().expiresTick()) {
            readyCheck = Optional.empty();
            readyResult = Optional.of(false);
        } else if (!removed.isEmpty()) {
            readyCheck = Optional.empty();
        }
        boolean changed =
                !invitations.equals(runtime.invitations())
                        || !members.equals(runtime.members())
                        || !readyCheck.equals(runtime.readyCheck());
        if (!changed) {
            return Result.success(PartyTransition.unchanged(runtime));
        }
        boolean disbanded = members.isEmpty();
        Optional<CharacterId> leader =
                disbanded
                        ? Optional.empty()
                        : runtime.leaderId()
                                .filter(members::containsKey)
                                .or(() -> nextLeader(members));
        Optional<CharacterId> leaderEffect =
                leader.equals(runtime.leaderId()) ? Optional.empty() : leader;
        return changed(
                runtime,
                leader,
                members,
                disbanded ? Map.of() : invitations,
                disbanded ? Optional.empty() : readyCheck,
                runtime.nextJoinedOrder(),
                disbanded,
                operationId,
                PartyOperationKind.CLOCK_ADVANCED,
                Set.of(),
                removed,
                leaderEffect,
                readyResult);
    }

    private Result<PartyTransition, PartyErrorCode> removeMember(
            PartyRuntime runtime, CharacterId targetId, UUID operationId, PartyOperationKind kind) {
        HashMap<CharacterId, PartyMember> members = new HashMap<>(runtime.members());
        members.remove(targetId);
        boolean disbanded = members.isEmpty();
        Optional<CharacterId> leader =
                disbanded
                        ? Optional.empty()
                        : runtime.leaderId()
                                .filter(members::containsKey)
                                .or(() -> nextLeader(members));
        Optional<CharacterId> leaderEffect =
                leader.equals(runtime.leaderId()) ? Optional.empty() : leader;
        return changed(
                runtime,
                leader,
                members,
                disbanded ? Map.of() : runtime.invitations(),
                Optional.empty(),
                runtime.nextJoinedOrder(),
                disbanded,
                operationId,
                kind,
                Set.of(),
                Set.of(targetId),
                leaderEffect,
                Optional.empty());
    }

    private static Optional<CharacterId> nextLeader(Map<CharacterId, PartyMember> members) {
        return members.values().stream()
                .min(
                        Comparator.comparingLong(PartyMember::joinedOrder)
                                .thenComparing(member -> member.characterId().value()))
                .map(PartyMember::characterId);
    }

    private static Result<PartyTransition, PartyErrorCode> changed(
            PartyRuntime source,
            Optional<CharacterId> leader,
            Map<CharacterId, PartyMember> members,
            Map<CharacterId, PartyInvitation> invitations,
            Optional<PartyReadyCheck> readyCheck,
            long nextJoinedOrder,
            boolean disbanded,
            UUID operationId,
            PartyOperationKind kind,
            Set<CharacterId> joined,
            Set<CharacterId> removed,
            Optional<CharacterId> newLeader,
            Optional<Boolean> readyResult) {
        HashMap<UUID, PartyOperationKind> operations = new HashMap<>(source.processedOperations());
        operations.put(operationId, kind);
        PartyRuntime runtime =
                new PartyRuntime(
                        source.partyId(),
                        leader,
                        members,
                        invitations,
                        readyCheck,
                        nextJoinedOrder,
                        disbanded,
                        operations);
        return Result.success(
                new PartyTransition(runtime, joined, removed, newLeader, readyResult, true));
    }

    private static Result<PartyTransition, PartyErrorCode> preflight(
            PartyRuntime runtime, UUID operationId, PartyOperationKind expectedKind) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(operationId, "operationId");
        PartyOperationKind existing = runtime.processedOperations().get(operationId);
        if (existing != null) {
            return existing == expectedKind
                    ? Result.success(PartyTransition.unchanged(runtime))
                    : failure(
                            PartyErrorCode.OPERATION_ID_REUSED,
                            "Operation ID was already used for " + existing + ".");
        }
        return runtime.disbanded()
                ? failure(PartyErrorCode.PARTY_DISBANDED, "Party is already disbanded.")
                : null;
    }

    private static Result<PartyTransition, PartyErrorCode> requireLeader(
            PartyRuntime runtime, CharacterId actorId) {
        Objects.requireNonNull(actorId, "actorId");
        return runtime.leaderId().orElseThrow().equals(actorId)
                ? null
                : failure(PartyErrorCode.NOT_LEADER, "Only the party leader may do that.");
    }

    private static Result<PartyTransition, PartyErrorCode> failure(
            PartyErrorCode error, String detail) {
        return Result.failure(error, detail);
    }

    private static void requireTick(long currentTick) {
        if (currentTick < 0) {
            throw new IllegalArgumentException("currentTick must not be negative");
        }
    }
}
