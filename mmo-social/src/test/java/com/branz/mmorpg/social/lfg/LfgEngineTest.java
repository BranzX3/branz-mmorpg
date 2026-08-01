package com.branz.mmorpg.social.lfg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.LfgListingId;
import com.branz.mmorpg.api.identity.PartyId;
import com.branz.mmorpg.api.result.Result;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LfgEngineTest {
    private final LfgEngine engine = new LfgEngine();

    @Test
    void defaultPolicyQueuesRequestForLeaderApproval() {
        CharacterId leader = character();
        CharacterId applicant = character();
        LfgListingRuntime runtime = listing(leader, 2);
        assertEquals(LfgJoinPolicy.LEADER_APPROVAL, runtime.joinPolicy());

        LfgTransition requested =
                success(
                        engine.requestJoin(
                                runtime, applicant, profile("quest.elite"), operation(), 10));
        runtime = requested.runtime();
        assertTrue(requested.acceptedApplicant().isEmpty());
        assertTrue(runtime.pendingRequests().containsKey(applicant));

        LfgTransition accepted =
                success(engine.decideRequest(runtime, leader, applicant, true, operation()));
        assertEquals(applicant, accepted.acceptedApplicant().orElseThrow());
        assertTrue(accepted.runtime().pendingRequests().isEmpty());
        assertEquals(1, accepted.runtime().remainingSlots());
    }

    @Test
    void requirementsUseOnlyBoundedPublicTags() {
        LfgListingRuntime runtime = listing(character(), 2);
        assertFailure(
                LfgErrorCode.REQUIREMENTS_NOT_MET,
                engine.requestJoin(runtime, character(), profile(), operation(), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LfgEntryRequirements(Set.of("combat.mastery.100")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LfgApplicantProfile(
                                LfgRolePreference.FLEXIBLE,
                                "line one\nline two",
                                Set.of("quest.elite")));
    }

    @Test
    void automaticPolicyAcceptsUntilStableCapacityBoundary() {
        CharacterId leader = character();
        LfgListingRuntime runtime =
                engine.start(
                        listingId(),
                        partyId(),
                        leader,
                        activity(),
                        region(),
                        "EN-us",
                        profile("quest.elite"),
                        requirements(),
                        LfgJoinPolicy.AUTOMATIC,
                        1);
        CharacterId first = character();
        LfgTransition accepted =
                success(engine.requestJoin(runtime, first, profile("quest.elite"), operation(), 1));
        assertEquals(first, accepted.acceptedApplicant().orElseThrow());
        assertEquals("en-us", accepted.runtime().language());
        assertFailure(
                LfgErrorCode.LISTING_FULL,
                engine.requestJoin(
                        accepted.runtime(), character(), profile("quest.elite"), operation(), 2));
    }

    @Test
    void leaderAuthorityCancellationAndCloseAreExplicit() {
        CharacterId leader = character();
        CharacterId applicant = character();
        LfgListingRuntime runtime =
                success(
                                engine.requestJoin(
                                        listing(leader, 2),
                                        applicant,
                                        profile("quest.elite"),
                                        operation(),
                                        4))
                        .runtime();
        assertFailure(
                LfgErrorCode.NOT_LEADER,
                engine.decideRequest(runtime, applicant, applicant, true, operation()));
        runtime = success(engine.cancelRequest(runtime, applicant, operation())).runtime();
        assertTrue(runtime.pendingRequests().isEmpty());
        runtime = success(engine.close(runtime, leader, operation())).runtime();
        assertTrue(runtime.closed());
        assertFailure(
                LfgErrorCode.LISTING_CLOSED,
                engine.requestJoin(runtime, character(), profile("quest.elite"), operation(), 5));
    }

    @Test
    void searchMatchesPublicActivityRegionLanguageRoleAndRequirements() {
        LfgListingRuntime runtime = listing(character(), 2);
        LfgSearchQuery matching =
                new LfgSearchQuery(
                        Optional.of(activity()),
                        Optional.of(region()),
                        Set.of("EN-US"),
                        Set.of(LfgRolePreference.SUPPORT),
                        profile("quest.elite"));
        assertTrue(engine.matches(runtime, matching));
        assertFalse(
                engine.matches(
                        runtime,
                        new LfgSearchQuery(
                                Optional.of(DefinitionId.of("activity.other")),
                                Optional.empty(),
                                Set.of(),
                                Set.of(),
                                profile("quest.elite"))));
        assertFalse(
                engine.matches(
                        runtime,
                        new LfgSearchQuery(
                                Optional.empty(),
                                Optional.empty(),
                                Set.of(),
                                Set.of(),
                                profile())));
    }

    @Test
    void exactOperationReplayIsNoOpAndCrossKindReuseFails() {
        CharacterId applicant = character();
        LfgListingRuntime runtime = listing(character(), 2);
        UUID operation = operation();
        LfgTransition first =
                success(
                        engine.requestJoin(
                                runtime, applicant, profile("quest.elite"), operation, 10));
        LfgTransition replay =
                success(
                        engine.requestJoin(
                                first.runtime(), applicant, profile("quest.elite"), operation, 10));
        assertFalse(replay.changed());
        assertFailure(
                LfgErrorCode.OPERATION_ID_REUSED,
                engine.cancelRequest(first.runtime(), applicant, operation));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        engine.requestJoin(
                                runtime, character(), profile("quest.elite"), operation(), -1));
    }

    private LfgListingRuntime listing(CharacterId leader, int availableSlots) {
        return engine.start(
                listingId(),
                partyId(),
                leader,
                activity(),
                region(),
                "en-us",
                new LfgApplicantProfile(
                        LfgRolePreference.SUPPORT, "Learning mechanics", Set.of("quest.elite")),
                requirements(),
                availableSlots);
    }

    private static LfgApplicantProfile profile(String... tags) {
        return new LfgApplicantProfile(LfgRolePreference.FLEXIBLE, "Experienced", Set.of(tags));
    }

    private static LfgEntryRequirements requirements() {
        return new LfgEntryRequirements(Set.of("quest.elite"));
    }

    private static DefinitionId activity() {
        return DefinitionId.of("activity.elite_hunt");
    }

    private static DefinitionId region() {
        return DefinitionId.of("region.ember_coast");
    }

    private static LfgListingId listingId() {
        return new LfgListingId(UUID.randomUUID());
    }

    private static PartyId partyId() {
        return new PartyId(UUID.randomUUID());
    }

    private static CharacterId character() {
        return new CharacterId(UUID.randomUUID());
    }

    private static UUID operation() {
        return UUID.randomUUID();
    }

    private static LfgTransition success(Result<LfgTransition, LfgErrorCode> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<LfgTransition, LfgErrorCode>) result).value();
    }

    private static void assertFailure(
            LfgErrorCode expected, Result<LfgTransition, LfgErrorCode> result) {
        assertFalse(result.isSuccess());
        assertEquals(expected, ((Result.Failure<LfgTransition, LfgErrorCode>) result).error());
    }
}
