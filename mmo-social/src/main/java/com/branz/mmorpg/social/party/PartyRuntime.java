package com.branz.mmorpg.social.party;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.PartyId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PartyRuntime(
        PartyId partyId,
        Optional<CharacterId> leaderId,
        Map<CharacterId, PartyMember> members,
        Map<CharacterId, PartyInvitation> invitations,
        Optional<PartyReadyCheck> readyCheck,
        long nextJoinedOrder,
        boolean disbanded,
        Map<UUID, PartyOperationKind> processedOperations) {
    public PartyRuntime {
        Objects.requireNonNull(partyId, "partyId");
        leaderId = Objects.requireNonNull(leaderId, "leaderId");
        Map<CharacterId, PartyMember> immutableMembers =
                Map.copyOf(Objects.requireNonNull(members, "members"));
        members = immutableMembers;
        Map<CharacterId, PartyInvitation> immutableInvitations =
                Map.copyOf(Objects.requireNonNull(invitations, "invitations"));
        invitations = immutableInvitations;
        readyCheck = Objects.requireNonNull(readyCheck, "readyCheck");
        processedOperations =
                Map.copyOf(Objects.requireNonNull(processedOperations, "processedOperations"));
        if (nextJoinedOrder < 1 || members.size() > PartyEngine.MAX_MEMBERS) {
            throw new IllegalArgumentException("invalid party size/order");
        }
        if (disbanded != members.isEmpty()
                || disbanded != leaderId.isEmpty()
                || disbanded && (!invitations.isEmpty() || readyCheck.isPresent())) {
            throw new IllegalArgumentException("invalid disbanded party state");
        }
        if (!disbanded && !members.containsKey(leaderId.orElseThrow())) {
            throw new IllegalArgumentException("leader must be a party member");
        }
        members.forEach(
                (characterId, member) -> {
                    if (!characterId.equals(member.characterId())) {
                        throw new IllegalArgumentException("member map key must match character");
                    }
                });
        invitations.forEach(
                (targetId, invitation) -> {
                    if (!targetId.equals(invitation.targetId())
                            || immutableMembers.containsKey(targetId)
                            || !immutableMembers.containsKey(invitation.invitedBy())) {
                        throw new IllegalArgumentException("invalid party invitation membership");
                    }
                });
        readyCheck.ifPresent(
                check -> {
                    if (!immutableMembers.containsKey(check.startedBy())
                            || !immutableMembers.keySet().containsAll(check.responses().keySet())) {
                        throw new IllegalArgumentException("invalid ready-check membership");
                    }
                });
    }
}
