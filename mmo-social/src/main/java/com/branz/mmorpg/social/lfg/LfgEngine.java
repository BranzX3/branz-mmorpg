package com.branz.mmorpg.social.lfg;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.LfgListingId;
import com.branz.mmorpg.api.identity.PartyId;
import com.branz.mmorpg.api.result.Result;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Pure LFG listing, application and leader-approval state machine. */
public final class LfgEngine {
    public LfgListingRuntime start(
            LfgListingId listingId,
            PartyId partyId,
            CharacterId leaderId,
            DefinitionId activityId,
            DefinitionId regionId,
            String language,
            LfgApplicantProfile leaderProfile,
            LfgEntryRequirements entryRequirements,
            int availableSlots) {
        return start(
                listingId,
                partyId,
                leaderId,
                activityId,
                regionId,
                language,
                leaderProfile,
                entryRequirements,
                LfgJoinPolicy.LEADER_APPROVAL,
                availableSlots);
    }

    public LfgListingRuntime start(
            LfgListingId listingId,
            PartyId partyId,
            CharacterId leaderId,
            DefinitionId activityId,
            DefinitionId regionId,
            String language,
            LfgApplicantProfile leaderProfile,
            LfgEntryRequirements entryRequirements,
            LfgJoinPolicy joinPolicy,
            int availableSlots) {
        return new LfgListingRuntime(
                listingId,
                partyId,
                leaderId,
                activityId,
                regionId,
                language,
                leaderProfile,
                entryRequirements,
                joinPolicy,
                availableSlots,
                Map.of(),
                Map.of(),
                false,
                Map.of());
    }

    public Result<LfgTransition, LfgErrorCode> requestJoin(
            LfgListingRuntime runtime,
            CharacterId applicantId,
            LfgApplicantProfile profile,
            UUID operationId,
            long currentTick) {
        Result<LfgTransition, LfgErrorCode> preflight =
                preflight(runtime, operationId, LfgOperationKind.REQUEST_JOIN);
        if (preflight != null) {
            return preflight;
        }
        requireTick(currentTick);
        Objects.requireNonNull(applicantId, "applicantId");
        Objects.requireNonNull(profile, "profile");
        if (runtime.leaderId().equals(applicantId)) {
            return failure(LfgErrorCode.LEADER_CANNOT_APPLY, "The listing leader cannot apply.");
        }
        if (runtime.acceptedApplicants().containsKey(applicantId)) {
            return failure(
                    LfgErrorCode.APPLICANT_ALREADY_ACCEPTED, "Applicant was already accepted.");
        }
        if (runtime.pendingRequests().containsKey(applicantId)) {
            return failure(
                    LfgErrorCode.REQUEST_ALREADY_EXISTS,
                    "Applicant already has a pending request.");
        }
        if (!runtime.entryRequirements().satisfiedBy(profile)) {
            return failure(
                    LfgErrorCode.REQUIREMENTS_NOT_MET,
                    "Applicant does not satisfy the public entry requirements.");
        }
        if (runtime.remainingSlots() == 0) {
            return failure(LfgErrorCode.LISTING_FULL, "The listing has no remaining slots.");
        }
        HashMap<CharacterId, LfgJoinRequest> pending = new HashMap<>(runtime.pendingRequests());
        HashMap<CharacterId, LfgApplicantProfile> accepted =
                new HashMap<>(runtime.acceptedApplicants());
        Optional<CharacterId> acceptedEffect = Optional.empty();
        if (runtime.joinPolicy() == LfgJoinPolicy.AUTOMATIC) {
            accepted.put(applicantId, profile);
            acceptedEffect = Optional.of(applicantId);
        } else {
            pending.put(applicantId, new LfgJoinRequest(applicantId, profile, currentTick));
        }
        return changed(
                runtime,
                pending,
                accepted,
                false,
                operationId,
                LfgOperationKind.REQUEST_JOIN,
                acceptedEffect);
    }

    public Result<LfgTransition, LfgErrorCode> decideRequest(
            LfgListingRuntime runtime,
            CharacterId leaderId,
            CharacterId applicantId,
            boolean accept,
            UUID operationId) {
        Result<LfgTransition, LfgErrorCode> preflight =
                preflight(runtime, operationId, LfgOperationKind.DECIDE_REQUEST);
        if (preflight != null) {
            return preflight;
        }
        Result<LfgTransition, LfgErrorCode> leader = requireLeader(runtime, leaderId);
        if (leader != null) {
            return leader;
        }
        LfgJoinRequest request = runtime.pendingRequests().get(applicantId);
        if (request == null) {
            return failure(LfgErrorCode.REQUEST_NOT_FOUND, "No pending request exists.");
        }
        if (accept && runtime.remainingSlots() == 0) {
            return failure(LfgErrorCode.LISTING_FULL, "The listing has no remaining slots.");
        }
        HashMap<CharacterId, LfgJoinRequest> pending = new HashMap<>(runtime.pendingRequests());
        pending.remove(applicantId);
        HashMap<CharacterId, LfgApplicantProfile> accepted =
                new HashMap<>(runtime.acceptedApplicants());
        if (accept) {
            accepted.put(applicantId, request.profile());
        }
        return changed(
                runtime,
                pending,
                accepted,
                false,
                operationId,
                LfgOperationKind.DECIDE_REQUEST,
                accept ? Optional.of(applicantId) : Optional.empty());
    }

    public Result<LfgTransition, LfgErrorCode> cancelRequest(
            LfgListingRuntime runtime, CharacterId applicantId, UUID operationId) {
        Result<LfgTransition, LfgErrorCode> preflight =
                preflight(runtime, operationId, LfgOperationKind.CANCEL_REQUEST);
        if (preflight != null) {
            return preflight;
        }
        if (!runtime.pendingRequests().containsKey(applicantId)) {
            return failure(LfgErrorCode.REQUEST_NOT_FOUND, "No pending request exists.");
        }
        HashMap<CharacterId, LfgJoinRequest> pending = new HashMap<>(runtime.pendingRequests());
        pending.remove(applicantId);
        return changed(
                runtime,
                pending,
                runtime.acceptedApplicants(),
                false,
                operationId,
                LfgOperationKind.CANCEL_REQUEST,
                Optional.empty());
    }

    public Result<LfgTransition, LfgErrorCode> close(
            LfgListingRuntime runtime, CharacterId leaderId, UUID operationId) {
        Result<LfgTransition, LfgErrorCode> preflight =
                preflight(runtime, operationId, LfgOperationKind.CLOSE);
        if (preflight != null) {
            return preflight;
        }
        Result<LfgTransition, LfgErrorCode> leader = requireLeader(runtime, leaderId);
        if (leader != null) {
            return leader;
        }
        return changed(
                runtime,
                Map.of(),
                runtime.acceptedApplicants(),
                true,
                operationId,
                LfgOperationKind.CLOSE,
                Optional.empty());
    }

    public boolean matches(LfgListingRuntime runtime, LfgSearchQuery query) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(query, "query");
        return !runtime.closed()
                && runtime.remainingSlots() > 0
                && query.activityId().map(runtime.activityId()::equals).orElse(true)
                && query.regionId().map(runtime.regionId()::equals).orElse(true)
                && (query.languages().isEmpty() || query.languages().contains(runtime.language()))
                && (query.roles().isEmpty()
                        || query.roles().contains(runtime.leaderProfile().rolePreference()))
                && runtime.entryRequirements().satisfiedBy(query.applicantProfile());
    }

    private static Result<LfgTransition, LfgErrorCode> changed(
            LfgListingRuntime source,
            Map<CharacterId, LfgJoinRequest> pending,
            Map<CharacterId, LfgApplicantProfile> accepted,
            boolean closed,
            UUID operationId,
            LfgOperationKind kind,
            Optional<CharacterId> acceptedEffect) {
        HashMap<UUID, LfgOperationKind> operations = new HashMap<>(source.processedOperations());
        operations.put(operationId, kind);
        LfgListingRuntime replacement =
                new LfgListingRuntime(
                        source.listingId(),
                        source.partyId(),
                        source.leaderId(),
                        source.activityId(),
                        source.regionId(),
                        source.language(),
                        source.leaderProfile(),
                        source.entryRequirements(),
                        source.joinPolicy(),
                        source.availableSlots(),
                        pending,
                        accepted,
                        closed,
                        operations);
        return Result.success(new LfgTransition(replacement, acceptedEffect, true));
    }

    private static Result<LfgTransition, LfgErrorCode> preflight(
            LfgListingRuntime runtime, UUID operationId, LfgOperationKind kind) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(operationId, "operationId");
        LfgOperationKind existing = runtime.processedOperations().get(operationId);
        if (existing != null) {
            return existing == kind
                    ? Result.success(LfgTransition.unchanged(runtime))
                    : failure(
                            LfgErrorCode.OPERATION_ID_REUSED,
                            "Operation ID was already used for " + existing + ".");
        }
        return runtime.closed()
                ? failure(LfgErrorCode.LISTING_CLOSED, "The LFG listing is closed.")
                : null;
    }

    private static Result<LfgTransition, LfgErrorCode> requireLeader(
            LfgListingRuntime runtime, CharacterId actorId) {
        Objects.requireNonNull(actorId, "actorId");
        return runtime.leaderId().equals(actorId)
                ? null
                : failure(LfgErrorCode.NOT_LEADER, "Only the listing leader may do that.");
    }

    private static Result<LfgTransition, LfgErrorCode> failure(LfgErrorCode error, String detail) {
        return Result.failure(error, detail);
    }

    private static void requireTick(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("currentTick must not be negative");
        }
    }
}
