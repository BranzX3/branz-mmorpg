package com.branz.mmorpg.social.pvp;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PvpMatchRuntime(
        EncounterId matchId,
        PvpMatchMode mode,
        PvpCombatProfile profile,
        CharacterId initiatedBy,
        Optional<CharacterId> challengedCharacter,
        Map<CharacterId, PvpParticipant> participants,
        PvpMatchPhase phase,
        long phaseEndsTick,
        Optional<PvpMatchResult> result,
        Map<UUID, PvpOperationKind> processedOperations) {
    public PvpMatchRuntime {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(initiatedBy, "initiatedBy");
        challengedCharacter = Objects.requireNonNull(challengedCharacter, "challengedCharacter");
        participants = Map.copyOf(Objects.requireNonNull(participants, "participants"));
        Objects.requireNonNull(phase, "phase");
        result = Objects.requireNonNull(result, "result");
        processedOperations =
                Map.copyOf(Objects.requireNonNull(processedOperations, "processedOperations"));
        if (participants.size() < 2 || participants.size() > PvpMatchEngine.MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("PvP match requires two to ten participants");
        }
        participants.forEach(
                (id, participant) -> {
                    if (!id.equals(participant.characterId())) {
                        throw new IllegalArgumentException("participant key must match character");
                    }
                });
        long teams = participants.values().stream().map(PvpParticipant::team).distinct().count();
        if (teams != 2) {
            throw new IllegalArgumentException("PvP match requires exactly two teams");
        }
        if (!participants.containsKey(initiatedBy)) {
            throw new IllegalArgumentException("initiator must be a participant");
        }
        if ((mode == PvpMatchMode.DUEL)
                != (participants.size() == 2 && challengedCharacter.isPresent())) {
            throw new IllegalArgumentException("duel identity must contain exactly two players");
        }
        if (challengedCharacter.isPresent()) {
            CharacterId challenged = challengedCharacter.orElseThrow();
            if (!participants.containsKey(challenged) || challenged.equals(initiatedBy)) {
                throw new IllegalArgumentException("challenged player is invalid");
            }
        }
        if ((phase == PvpMatchPhase.CHALLENGED || phase == PvpMatchPhase.COUNTDOWN)
                != (phaseEndsTick >= 0)) {
            throw new IllegalArgumentException(
                    "phase expiry must match challenged/countdown state");
        }
        if ((phase == PvpMatchPhase.COMPLETED) != result.isPresent()) {
            throw new IllegalArgumentException("only completed matches carry a result");
        }
    }
}
