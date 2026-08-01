package com.branz.mmorpg.social.downed;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record DownedEncounterRuntime(
        EncounterId encounterId,
        Map<CharacterId, DownedParticipant> participants,
        Map<CharacterId, ReviveChannel> reviveChannelsByTarget,
        Map<UUID, DownedOperationKind> processedOperations) {
    public DownedEncounterRuntime {
        Objects.requireNonNull(encounterId, "encounterId");
        Map<CharacterId, DownedParticipant> immutableParticipants =
                Map.copyOf(Objects.requireNonNull(participants, "participants"));
        participants = immutableParticipants;
        Map<CharacterId, ReviveChannel> immutableChannels =
                Map.copyOf(
                        Objects.requireNonNull(reviveChannelsByTarget, "reviveChannelsByTarget"));
        reviveChannelsByTarget = immutableChannels;
        processedOperations =
                Map.copyOf(Objects.requireNonNull(processedOperations, "processedOperations"));
        if (participants.isEmpty()
                || participants.size() > DownedEncounterEngine.MAX_PARTICIPANTS) {
            throw new IllegalArgumentException(
                    "downed encounter requires one to five participants");
        }
        participants.forEach(
                (characterId, participant) -> {
                    if (!characterId.equals(participant.characterId())) {
                        throw new IllegalArgumentException(
                                "participant map key must match character");
                    }
                });
        immutableChannels.forEach(
                (targetId, channel) -> {
                    if (!targetId.equals(channel.targetId())
                            || !immutableParticipants.containsKey(targetId)
                            || !immutableParticipants.containsKey(channel.reviverId())
                            || immutableParticipants.get(targetId).lifeState()
                                    != EncounterLifeState.DOWNED
                            || immutableParticipants.get(channel.reviverId()).lifeState()
                                    != EncounterLifeState.ACTIVE) {
                        throw new IllegalArgumentException(
                                "revive channel participants are invalid");
                    }
                });
        long distinctRevivers =
                immutableChannels.values().stream()
                        .map(ReviveChannel::reviverId)
                        .distinct()
                        .count();
        if (distinctRevivers != immutableChannels.size()) {
            throw new IllegalArgumentException("one participant can channel only one revive");
        }
    }
}
