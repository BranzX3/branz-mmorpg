package com.branz.mmorpg.worldloop.encounter;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.EncounterId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable authoritative state for one boss encounter across retry attempts. */
public record BossEncounterRuntime(
        EncounterId encounterId,
        DefinitionId definitionId,
        UUID checkpointInstanceId,
        BossEncounterPhase phase,
        int attempt,
        long startedTick,
        Map<CharacterId, EncounterParticipant> participants,
        Map<UUID, EncounterOperationKind> processedOperations,
        Optional<UUID> activeResetOperationId,
        Optional<UUID> rewardGrantId) {
    public BossEncounterRuntime {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(checkpointInstanceId, "checkpointInstanceId");
        Objects.requireNonNull(phase, "phase");
        participants = Map.copyOf(Objects.requireNonNull(participants, "participants"));
        processedOperations =
                Map.copyOf(Objects.requireNonNull(processedOperations, "processedOperations"));
        activeResetOperationId =
                Objects.requireNonNull(activeResetOperationId, "activeResetOperationId");
        rewardGrantId = Objects.requireNonNull(rewardGrantId, "rewardGrantId");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        if (startedTick < 0) {
            throw new IllegalArgumentException("startedTick must not be negative");
        }
        if (participants.isEmpty() || participants.size() > BossEncounterEngine.MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("boss encounter requires one to five participants");
        }
        participants.forEach(
                (characterId, participant) -> {
                    if (!characterId.equals(participant.characterId())) {
                        throw new IllegalArgumentException(
                                "participant map key must match the participant character");
                    }
                });
        if ((phase == BossEncounterPhase.RESETTING) != activeResetOperationId.isPresent()) {
            throw new IllegalArgumentException("only resetting encounters have an active reset");
        }
        if ((phase == BossEncounterPhase.COMPLETED) != rewardGrantId.isPresent()) {
            throw new IllegalArgumentException("only completed encounters have a reward grant");
        }
    }
}
