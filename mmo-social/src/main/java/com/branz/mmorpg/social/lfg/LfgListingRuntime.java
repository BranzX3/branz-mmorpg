package com.branz.mmorpg.social.lfg;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.LfgListingId;
import com.branz.mmorpg.api.identity.PartyId;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record LfgListingRuntime(
        LfgListingId listingId,
        PartyId partyId,
        CharacterId leaderId,
        DefinitionId activityId,
        DefinitionId regionId,
        String language,
        LfgApplicantProfile leaderProfile,
        LfgEntryRequirements entryRequirements,
        LfgJoinPolicy joinPolicy,
        int availableSlots,
        Map<CharacterId, LfgJoinRequest> pendingRequests,
        Map<CharacterId, LfgApplicantProfile> acceptedApplicants,
        boolean closed,
        Map<UUID, LfgOperationKind> processedOperations) {
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,8}(?:-[a-z0-9]{1,8})*");

    public LfgListingRuntime {
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(leaderId, "leaderId");
        Objects.requireNonNull(activityId, "activityId");
        Objects.requireNonNull(regionId, "regionId");
        language = Objects.requireNonNull(language, "language").toLowerCase(Locale.ROOT);
        Objects.requireNonNull(leaderProfile, "leaderProfile");
        Objects.requireNonNull(entryRequirements, "entryRequirements");
        Objects.requireNonNull(joinPolicy, "joinPolicy");
        pendingRequests = Map.copyOf(Objects.requireNonNull(pendingRequests, "pendingRequests"));
        acceptedApplicants =
                Map.copyOf(Objects.requireNonNull(acceptedApplicants, "acceptedApplicants"));
        processedOperations =
                Map.copyOf(Objects.requireNonNull(processedOperations, "processedOperations"));
        if (!LANGUAGE.matcher(language).matches()) {
            throw new IllegalArgumentException("invalid LFG language tag");
        }
        if (availableSlots < 1 || availableSlots > 4) {
            throw new IllegalArgumentException("availableSlots must be from one to four");
        }
        if (acceptedApplicants.size() > availableSlots) {
            throw new IllegalArgumentException("accepted applicants exceed available slots");
        }
        if (closed && !pendingRequests.isEmpty()) {
            throw new IllegalArgumentException("closed listing cannot retain pending requests");
        }
        if (pendingRequests.containsKey(leaderId)
                || acceptedApplicants.containsKey(leaderId)
                || pendingRequests.keySet().stream().anyMatch(acceptedApplicants::containsKey)) {
            throw new IllegalArgumentException("invalid LFG applicant membership");
        }
        pendingRequests.forEach(
                (applicantId, request) -> {
                    if (!applicantId.equals(request.applicantId())) {
                        throw new IllegalArgumentException("request key must match applicant");
                    }
                });
    }

    public int remainingSlots() {
        return availableSlots - acceptedApplicants.size();
    }
}
