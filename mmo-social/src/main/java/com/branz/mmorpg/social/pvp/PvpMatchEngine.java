package com.branz.mmorpg.social.pvp;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/** Pure consent, countdown, hostile-permission and terminal-defeat PvP state machine. */
public final class PvpMatchEngine {
    public static final int MAX_PARTICIPANTS = 10;
    public static final long CHALLENGE_DURATION_TICKS = 600;
    public static final long COUNTDOWN_TICKS = 100;
    public static final long DISCONNECT_GRACE_TICKS = 200;

    public Result<PvpTransition, PvpErrorCode> challengeDuel(
            EncounterId matchId,
            CharacterId challenger,
            CharacterId target,
            PvpAdmission challengerAdmission,
            PvpAdmission targetAdmission,
            PvpCombatProfile profile,
            UUID operationId,
            long currentTick) {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(challenger, "challenger");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(challengerAdmission, "challengerAdmission");
        Objects.requireNonNull(targetAdmission, "targetAdmission");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(operationId, "operationId");
        requireTick(currentTick);
        if (challenger.equals(target)) {
            return failure(PvpErrorCode.PARTICIPANT_INVALID, "A character cannot duel itself.");
        }
        Result<PvpTransition, PvpErrorCode> admission =
                requireAdmission(Map.of(challenger, challengerAdmission, target, targetAdmission));
        if (admission != null) {
            return admission;
        }
        Map<CharacterId, PvpParticipant> participants =
                Map.of(
                        challenger, PvpParticipant.ready(challenger, 0),
                        target, PvpParticipant.ready(target, 1));
        PvpMatchRuntime runtime =
                new PvpMatchRuntime(
                        matchId,
                        PvpMatchMode.DUEL,
                        profile,
                        challenger,
                        Optional.of(target),
                        participants,
                        PvpMatchPhase.CHALLENGED,
                        Math.addExact(currentTick, CHALLENGE_DURATION_TICKS),
                        Optional.empty(),
                        Map.of(operationId, PvpOperationKind.CHALLENGE));
        return Result.success(new PvpTransition(runtime, false, Set.of(), Optional.empty(), true));
    }

    public Result<PvpTransition, PvpErrorCode> startArena(
            EncounterId matchId,
            CharacterId initiatedBy,
            Map<CharacterId, Integer> teams,
            Map<CharacterId, PvpAdmission> admissions,
            PvpCombatProfile profile,
            UUID operationId,
            long currentTick) {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(initiatedBy, "initiatedBy");
        Objects.requireNonNull(teams, "teams");
        Objects.requireNonNull(admissions, "admissions");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(operationId, "operationId");
        requireTick(currentTick);
        if (teams.size() < 2
                || teams.size() > MAX_PARTICIPANTS
                || !teams.containsKey(initiatedBy)
                || !teams.keySet().equals(admissions.keySet())
                || teams.values().stream().anyMatch(team -> team == null || team < 0 || team > 1)
                || teams.values().stream().distinct().count() != 2) {
            return failure(
                    PvpErrorCode.TEAM_INVALID,
                    "Arena requires two non-empty teams and matching admissions.");
        }
        Result<PvpTransition, PvpErrorCode> admission = requireAdmission(admissions);
        if (admission != null) {
            return admission;
        }
        HashMap<CharacterId, PvpParticipant> participants = new HashMap<>();
        teams.forEach(
                (characterId, team) ->
                        participants.put(characterId, PvpParticipant.ready(characterId, team)));
        PvpMatchRuntime runtime =
                new PvpMatchRuntime(
                        matchId,
                        PvpMatchMode.ARENA,
                        profile,
                        initiatedBy,
                        Optional.empty(),
                        participants,
                        PvpMatchPhase.COUNTDOWN,
                        Math.addExact(currentTick, COUNTDOWN_TICKS),
                        Optional.empty(),
                        Map.of(operationId, PvpOperationKind.ARENA_START));
        return Result.success(new PvpTransition(runtime, false, Set.of(), Optional.empty(), true));
    }

    public Result<PvpTransition, PvpErrorCode> accept(
            PvpMatchRuntime runtime, CharacterId target, UUID operationId, long currentTick) {
        Result<PvpTransition, PvpErrorCode> preflight =
                preflight(runtime, operationId, PvpOperationKind.ACCEPT);
        if (preflight != null) {
            return preflight;
        }
        requireTick(currentTick);
        if (runtime.phase() != PvpMatchPhase.CHALLENGED) {
            return invalidState("Only a pending duel challenge can be accepted.");
        }
        if (!runtime.challengedCharacter().orElseThrow().equals(target)) {
            return failure(
                    PvpErrorCode.PARTICIPANT_INVALID, "Only the challenged character may accept.");
        }
        if (currentTick >= runtime.phaseEndsTick()) {
            return failure(PvpErrorCode.CHALLENGE_EXPIRED, "Duel challenge has expired.");
        }
        return changed(
                runtime,
                runtime.participants(),
                PvpMatchPhase.COUNTDOWN,
                Math.addExact(currentTick, COUNTDOWN_TICKS),
                Optional.empty(),
                operationId,
                PvpOperationKind.ACCEPT,
                false,
                Set.of());
    }

    public Result<PvpTransition, PvpErrorCode> decline(
            PvpMatchRuntime runtime, CharacterId target, UUID operationId) {
        Result<PvpTransition, PvpErrorCode> preflight =
                preflight(runtime, operationId, PvpOperationKind.DECLINE);
        if (preflight != null) {
            return preflight;
        }
        if (runtime.phase() != PvpMatchPhase.CHALLENGED
                || !runtime.challengedCharacter().orElseThrow().equals(target)) {
            return invalidState("Only the challenged character may decline a pending duel.");
        }
        return complete(
                runtime,
                runtime.participants(),
                new PvpMatchResult(OptionalInt.empty(), Set.of(), PvpCompletionReason.DECLINED),
                operationId,
                PvpOperationKind.DECLINE,
                Set.of());
    }

    public Result<PvpTransition, PvpErrorCode> advance(
            PvpMatchRuntime runtime, UUID operationId, long currentTick) {
        Result<PvpTransition, PvpErrorCode> preflight =
                preflight(runtime, operationId, PvpOperationKind.CLOCK_ADVANCED);
        if (preflight != null) {
            return preflight;
        }
        requireTick(currentTick);
        if (runtime.phase() == PvpMatchPhase.CHALLENGED) {
            if (currentTick < runtime.phaseEndsTick()) {
                return Result.success(PvpTransition.unchanged(runtime));
            }
            return complete(
                    runtime,
                    runtime.participants(),
                    new PvpMatchResult(
                            OptionalInt.empty(), Set.of(), PvpCompletionReason.CHALLENGE_EXPIRED),
                    operationId,
                    PvpOperationKind.CLOCK_ADVANCED,
                    Set.of());
        }
        if (runtime.phase() == PvpMatchPhase.COUNTDOWN) {
            if (currentTick < runtime.phaseEndsTick()) {
                return Result.success(PvpTransition.unchanged(runtime));
            }
            return changed(
                    runtime,
                    runtime.participants(),
                    PvpMatchPhase.ACTIVE,
                    -1,
                    Optional.empty(),
                    operationId,
                    PvpOperationKind.CLOCK_ADVANCED,
                    true,
                    Set.of());
        }
        if (runtime.phase() != PvpMatchPhase.ACTIVE) {
            return Result.success(PvpTransition.unchanged(runtime));
        }
        HashSet<CharacterId> timedOut = new HashSet<>();
        HashMap<CharacterId, PvpParticipant> participants = new HashMap<>(runtime.participants());
        participants.values().stream()
                .filter(value -> value.status() == PvpParticipantStatus.DISCONNECTED_GRACE)
                .filter(value -> currentTick >= value.disconnectExpiresTick())
                .sorted(Comparator.comparing(value -> value.characterId().value()))
                .forEach(
                        value -> {
                            timedOut.add(value.characterId());
                            participants.put(
                                    value.characterId(),
                                    new PvpParticipant(
                                            value.characterId(),
                                            value.team(),
                                            PvpParticipantStatus.DEFEATED,
                                            -1));
                        });
        if (timedOut.isEmpty()) {
            return Result.success(PvpTransition.unchanged(runtime));
        }
        return resolveDefeats(
                runtime,
                participants,
                timedOut,
                PvpCompletionReason.DISCONNECT_TIMEOUT,
                operationId,
                PvpOperationKind.CLOCK_ADVANCED);
    }

    public Result<PvpTransition, PvpErrorCode> defeat(
            PvpMatchRuntime runtime, CharacterId loser, UUID operationId) {
        Result<PvpTransition, PvpErrorCode> preflight =
                preflight(runtime, operationId, PvpOperationKind.DEFEAT);
        if (preflight != null) {
            return preflight;
        }
        Result<PvpTransition, PvpErrorCode> active = requireActiveParticipant(runtime, loser);
        if (active != null) {
            return active;
        }
        HashMap<CharacterId, PvpParticipant> participants = new HashMap<>(runtime.participants());
        PvpParticipant participant = participants.get(loser);
        participants.put(
                loser,
                new PvpParticipant(loser, participant.team(), PvpParticipantStatus.DEFEATED, -1));
        return resolveDefeats(
                runtime,
                participants,
                Set.of(loser),
                PvpCompletionReason.DEFEAT,
                operationId,
                PvpOperationKind.DEFEAT);
    }

    public Result<PvpTransition, PvpErrorCode> surrender(
            PvpMatchRuntime runtime, CharacterId actor, UUID operationId) {
        return forfeitTeam(
                runtime,
                actor,
                PvpCompletionReason.SURRENDER,
                operationId,
                PvpOperationKind.SURRENDER);
    }

    public Result<PvpTransition, PvpErrorCode> boundaryForfeit(
            PvpMatchRuntime runtime, CharacterId actor, UUID operationId) {
        return forfeitTeam(
                runtime,
                actor,
                PvpCompletionReason.BOUNDARY_FORFEIT,
                operationId,
                PvpOperationKind.BOUNDARY_FORFEIT);
    }

    public Result<PvpTransition, PvpErrorCode> disconnect(
            PvpMatchRuntime runtime, CharacterId actor, UUID operationId, long currentTick) {
        Result<PvpTransition, PvpErrorCode> preflight =
                preflight(runtime, operationId, PvpOperationKind.DISCONNECT);
        if (preflight != null) {
            return preflight;
        }
        requireTick(currentTick);
        Result<PvpTransition, PvpErrorCode> active = requireActiveParticipant(runtime, actor);
        if (active != null) {
            return active;
        }
        HashMap<CharacterId, PvpParticipant> participants = new HashMap<>(runtime.participants());
        PvpParticipant participant = participants.get(actor);
        participants.put(
                actor,
                new PvpParticipant(
                        actor,
                        participant.team(),
                        PvpParticipantStatus.DISCONNECTED_GRACE,
                        Math.addExact(currentTick, DISCONNECT_GRACE_TICKS)));
        return changed(
                runtime,
                participants,
                runtime.phase(),
                runtime.phaseEndsTick(),
                Optional.empty(),
                operationId,
                PvpOperationKind.DISCONNECT,
                false,
                Set.of());
    }

    public Result<PvpTransition, PvpErrorCode> reconnect(
            PvpMatchRuntime runtime, CharacterId actor, UUID operationId, long currentTick) {
        Result<PvpTransition, PvpErrorCode> preflight =
                preflight(runtime, operationId, PvpOperationKind.RECONNECT);
        if (preflight != null) {
            return preflight;
        }
        requireTick(currentTick);
        if (runtime.phase() != PvpMatchPhase.ACTIVE) {
            return invalidState("Only an active PvP match can reconnect a participant.");
        }
        PvpParticipant participant = runtime.participants().get(actor);
        if (participant == null
                || participant.status() != PvpParticipantStatus.DISCONNECTED_GRACE) {
            return failure(
                    PvpErrorCode.PARTICIPANT_INVALID, "Participant is not in disconnect grace.");
        }
        if (currentTick >= participant.disconnectExpiresTick()) {
            return invalidState("Disconnect grace already expired.");
        }
        HashMap<CharacterId, PvpParticipant> participants = new HashMap<>(runtime.participants());
        participants.put(actor, PvpParticipant.ready(actor, participant.team()));
        return changed(
                runtime,
                participants,
                runtime.phase(),
                runtime.phaseEndsTick(),
                Optional.empty(),
                operationId,
                PvpOperationKind.RECONNECT,
                false,
                Set.of());
    }

    public Result<PvpTransition, PvpErrorCode> cancel(
            PvpMatchRuntime runtime, CharacterId actor, UUID operationId) {
        Result<PvpTransition, PvpErrorCode> preflight =
                preflight(runtime, operationId, PvpOperationKind.CANCEL);
        if (preflight != null) {
            return preflight;
        }
        if (!runtime.initiatedBy().equals(actor)
                || (runtime.phase() != PvpMatchPhase.CHALLENGED
                        && runtime.phase() != PvpMatchPhase.COUNTDOWN)) {
            return invalidState("Only the initiator may cancel before PvP becomes active.");
        }
        return complete(
                runtime,
                runtime.participants(),
                new PvpMatchResult(OptionalInt.empty(), Set.of(), PvpCompletionReason.CANCELLED),
                operationId,
                PvpOperationKind.CANCEL,
                Set.of());
    }

    public boolean hostileAllowed(
            PvpMatchRuntime runtime, CharacterId attacker, CharacterId defender) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(defender, "defender");
        if (runtime.phase() != PvpMatchPhase.ACTIVE || attacker.equals(defender)) {
            return false;
        }
        PvpParticipant source = runtime.participants().get(attacker);
        PvpParticipant target = runtime.participants().get(defender);
        return source != null
                && target != null
                && source.status() == PvpParticipantStatus.READY
                && target.status() == PvpParticipantStatus.READY
                && source.team() != target.team();
    }

    private Result<PvpTransition, PvpErrorCode> forfeitTeam(
            PvpMatchRuntime runtime,
            CharacterId actor,
            PvpCompletionReason reason,
            UUID operationId,
            PvpOperationKind operationKind) {
        Result<PvpTransition, PvpErrorCode> preflight =
                preflight(runtime, operationId, operationKind);
        if (preflight != null) {
            return preflight;
        }
        Result<PvpTransition, PvpErrorCode> active = requireActiveParticipant(runtime, actor);
        if (active != null) {
            return active;
        }
        int losingTeam = runtime.participants().get(actor).team();
        HashMap<CharacterId, PvpParticipant> participants = new HashMap<>(runtime.participants());
        HashSet<CharacterId> defeated = new HashSet<>();
        participants.values().stream()
                .filter(participant -> participant.team() == losingTeam)
                .forEach(
                        participant -> {
                            defeated.add(participant.characterId());
                            participants.put(
                                    participant.characterId(),
                                    new PvpParticipant(
                                            participant.characterId(),
                                            participant.team(),
                                            PvpParticipantStatus.DEFEATED,
                                            -1));
                        });
        return resolveDefeats(runtime, participants, defeated, reason, operationId, operationKind);
    }

    private Result<PvpTransition, PvpErrorCode> resolveDefeats(
            PvpMatchRuntime runtime,
            Map<CharacterId, PvpParticipant> participants,
            Set<CharacterId> newlyDefeated,
            PvpCompletionReason reason,
            UUID operationId,
            PvpOperationKind operationKind) {
        Set<Integer> survivingTeams = new HashSet<>();
        participants.values().stream()
                .filter(participant -> participant.status() != PvpParticipantStatus.DEFEATED)
                .map(PvpParticipant::team)
                .forEach(survivingTeams::add);
        if (survivingTeams.size() > 1) {
            return changed(
                    runtime,
                    participants,
                    runtime.phase(),
                    runtime.phaseEndsTick(),
                    Optional.empty(),
                    operationId,
                    operationKind,
                    false,
                    newlyDefeated);
        }
        OptionalInt winner =
                survivingTeams.isEmpty()
                        ? OptionalInt.empty()
                        : OptionalInt.of(survivingTeams.iterator().next());
        Set<CharacterId> allDefeated =
                participants.values().stream()
                        .filter(value -> value.status() == PvpParticipantStatus.DEFEATED)
                        .map(PvpParticipant::characterId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return complete(
                runtime,
                participants,
                new PvpMatchResult(winner, allDefeated, reason),
                operationId,
                operationKind,
                newlyDefeated);
    }

    private Result<PvpTransition, PvpErrorCode> complete(
            PvpMatchRuntime runtime,
            Map<CharacterId, PvpParticipant> participants,
            PvpMatchResult result,
            UUID operationId,
            PvpOperationKind operationKind,
            Set<CharacterId> newlyDefeated) {
        return changed(
                runtime,
                participants,
                PvpMatchPhase.COMPLETED,
                -1,
                Optional.of(result),
                operationId,
                operationKind,
                false,
                newlyDefeated);
    }

    private Result<PvpTransition, PvpErrorCode> changed(
            PvpMatchRuntime runtime,
            Map<CharacterId, PvpParticipant> participants,
            PvpMatchPhase phase,
            long phaseEndsTick,
            Optional<PvpMatchResult> result,
            UUID operationId,
            PvpOperationKind operationKind,
            boolean newlyActive,
            Set<CharacterId> newlyDefeated) {
        HashMap<UUID, PvpOperationKind> processed = new HashMap<>(runtime.processedOperations());
        processed.put(operationId, operationKind);
        PvpMatchRuntime replacement =
                new PvpMatchRuntime(
                        runtime.matchId(),
                        runtime.mode(),
                        runtime.profile(),
                        runtime.initiatedBy(),
                        runtime.challengedCharacter(),
                        participants,
                        phase,
                        phaseEndsTick,
                        result,
                        processed);
        return Result.success(
                new PvpTransition(replacement, newlyActive, newlyDefeated, result, true));
    }

    private Result<PvpTransition, PvpErrorCode> preflight(
            PvpMatchRuntime runtime, UUID operationId, PvpOperationKind operationKind) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(operationKind, "operationKind");
        PvpOperationKind processed = runtime.processedOperations().get(operationId);
        if (processed == operationKind) {
            return Result.success(PvpTransition.unchanged(runtime));
        }
        if (processed != null) {
            return failure(
                    PvpErrorCode.OPERATION_ID_REUSED,
                    "PvP operation ID was already used for " + processed + ".");
        }
        return null;
    }

    private Result<PvpTransition, PvpErrorCode> requireActiveParticipant(
            PvpMatchRuntime runtime, CharacterId actor) {
        Objects.requireNonNull(actor, "actor");
        if (runtime.phase() != PvpMatchPhase.ACTIVE) {
            return invalidState("PvP match is not active.");
        }
        PvpParticipant participant = runtime.participants().get(actor);
        if (participant == null || participant.status() != PvpParticipantStatus.READY) {
            return failure(
                    PvpErrorCode.PARTICIPANT_INVALID,
                    "Character is not an active PvP participant.");
        }
        return null;
    }

    private static Result<PvpTransition, PvpErrorCode> requireAdmission(
            Map<CharacterId, PvpAdmission> admissions) {
        ArrayList<CharacterId> rejected =
                admissions.entrySet().stream()
                        .filter(entry -> !entry.getValue().accepted())
                        .map(Map.Entry::getKey)
                        .sorted(Comparator.comparing(CharacterId::value))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return rejected.isEmpty()
                ? null
                : failure(
                        PvpErrorCode.ADMISSION_REJECTED,
                        "PvP admission rejected for " + rejected.getFirst().value() + ".");
    }

    private static Result<PvpTransition, PvpErrorCode> invalidState(String detail) {
        return failure(PvpErrorCode.MATCH_INVALID_STATE, detail);
    }

    private static Result<PvpTransition, PvpErrorCode> failure(PvpErrorCode error, String detail) {
        return Result.failure(error, detail);
    }

    private static void requireTick(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("currentTick must not be negative");
        }
    }
}
