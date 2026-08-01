package com.branz.mmorpg.social.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.PartyId;
import com.branz.mmorpg.api.result.Result;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartyEngineTest {
    private final PartyEngine engine = new PartyEngine();

    @Test
    void leaderInvitesAndTargetsAcceptOrDeclineWithinCapacity() {
        CharacterId leader = character();
        PartyRuntime runtime = engine.start(party(), leader);
        CharacterId accepted = character();
        runtime = success(engine.invite(runtime, leader, accepted, operation(), 10)).runtime();
        PartyTransition joined = success(engine.accept(runtime, accepted, operation(), 11));
        runtime = joined.runtime();
        assertEquals(java.util.Set.of(accepted), joined.joined());

        CharacterId declined = character();
        runtime = success(engine.invite(runtime, leader, declined, operation(), 12)).runtime();
        runtime = success(engine.decline(runtime, declined, operation(), 13)).runtime();
        assertFalse(runtime.invitations().containsKey(declined));

        for (int index = 0; index < 3; index++) {
            CharacterId member = character();
            runtime =
                    success(engine.invite(runtime, leader, member, operation(), 20 + index))
                            .runtime();
            runtime = success(engine.accept(runtime, member, operation(), 30 + index)).runtime();
        }
        CharacterId sixth = character();
        runtime = success(engine.invite(runtime, leader, sixth, operation(), 40)).runtime();
        assertFailure(PartyErrorCode.PARTY_FULL, engine.accept(runtime, sixth, operation(), 41));
        assertFailure(
                PartyErrorCode.NOT_LEADER,
                engine.invite(runtime, accepted, character(), operation(), 42));
    }

    @Test
    void invitationExpiryFailsWithoutMembershipMutation() {
        CharacterId leader = character();
        CharacterId target = character();
        PartyRuntime runtime = engine.start(party(), leader);
        runtime = success(engine.invite(runtime, leader, target, operation(), 100)).runtime();

        assertFailure(
                PartyErrorCode.INVITATION_EXPIRED,
                engine.accept(
                        runtime, target, operation(), 100 + PartyEngine.INVITATION_DURATION_TICKS));
        PartyTransition advanced =
                success(
                        engine.advance(
                                runtime, operation(), 100 + PartyEngine.INVITATION_DURATION_TICKS));
        assertFalse(advanced.runtime().invitations().containsKey(target));
    }

    @Test
    void transferKickAndLeaveChooseStableLeaderThenDisband() {
        CharacterId first = character();
        CharacterId second = character();
        CharacterId third = character();
        PartyRuntime runtime = partyWith(first, second, third);

        PartyTransition transferred =
                success(engine.transferLeader(runtime, first, third, operation()));
        runtime = transferred.runtime();
        assertEquals(third, transferred.newLeader().orElseThrow());
        runtime = success(engine.kick(runtime, third, second, operation())).runtime();
        PartyTransition leaderLeft = success(engine.leave(runtime, third, operation()));
        runtime = leaderLeft.runtime();
        assertEquals(first, leaderLeft.newLeader().orElseThrow());
        PartyTransition disbanded = success(engine.leave(runtime, first, operation()));
        assertTrue(disbanded.runtime().disbanded());
        assertTrue(disbanded.runtime().members().isEmpty());
    }

    @Test
    void reconnectPreservesMembershipAndTimeoutTransfersLeadership() {
        CharacterId leader = character();
        CharacterId second = character();
        PartyRuntime runtime = partyWith(leader, second);
        runtime = success(engine.disconnect(runtime, leader, operation(), 50)).runtime();
        runtime = success(engine.reconnect(runtime, leader, operation(), 60)).runtime();
        assertEquals(PartyMemberStatus.ONLINE, runtime.members().get(leader).status());

        runtime = success(engine.disconnect(runtime, leader, operation(), 100)).runtime();
        PartyTransition expired =
                success(
                        engine.advance(
                                runtime, operation(), 100 + PartyEngine.DISCONNECT_GRACE_TICKS));
        assertEquals(java.util.Set.of(leader), expired.removed());
        assertEquals(second, expired.newLeader().orElseThrow());
    }

    @Test
    void readyCheckCompletesFalseOrTimesOutWithoutStickyResponses() {
        CharacterId leader = character();
        CharacterId second = character();
        CharacterId third = character();
        PartyRuntime runtime = partyWith(leader, second, third);
        runtime =
                success(engine.startReadyCheck(runtime, leader, operation(), operation(), 200))
                        .runtime();
        runtime = success(engine.respondReady(runtime, second, true, operation(), 201)).runtime();
        PartyTransition completed =
                success(engine.respondReady(runtime, third, false, operation(), 202));
        assertEquals(false, completed.readyCheckResult().orElseThrow());
        assertTrue(completed.runtime().readyCheck().isEmpty());

        runtime =
                success(
                                engine.startReadyCheck(
                                        completed.runtime(), leader, operation(), operation(), 300))
                        .runtime();
        PartyTransition timedOut =
                success(
                        engine.advance(
                                runtime,
                                operation(),
                                300 + PartyEngine.READY_CHECK_DURATION_TICKS));
        assertEquals(false, timedOut.readyCheckResult().orElseThrow());
        assertTrue(timedOut.runtime().readyCheck().isEmpty());
    }

    @Test
    void operationReplayIsNoOpAndCrossKindReuseFails() {
        CharacterId leader = character();
        CharacterId target = character();
        PartyRuntime runtime = engine.start(party(), leader);
        UUID operation = operation();
        PartyTransition first = success(engine.invite(runtime, leader, target, operation, 10));

        PartyTransition replay =
                success(engine.invite(first.runtime(), leader, target, operation, 10));
        assertFalse(replay.changed());
        assertFailure(
                PartyErrorCode.OPERATION_ID_REUSED,
                engine.decline(first.runtime(), target, operation, 11));
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.advance(first.runtime(), operation(), -1));
    }

    private PartyRuntime partyWith(CharacterId leader, CharacterId... others) {
        PartyRuntime runtime = engine.start(party(), leader);
        long tick = 1;
        for (CharacterId other : List.of(others)) {
            runtime = success(engine.invite(runtime, leader, other, operation(), tick++)).runtime();
            runtime = success(engine.accept(runtime, other, operation(), tick++)).runtime();
        }
        return runtime;
    }

    private static PartyId party() {
        return new PartyId(UUID.randomUUID());
    }

    private static CharacterId character() {
        return new CharacterId(UUID.randomUUID());
    }

    private static UUID operation() {
        return UUID.randomUUID();
    }

    private static PartyTransition success(Result<PartyTransition, PartyErrorCode> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<PartyTransition, PartyErrorCode>) result).value();
    }

    private static void assertFailure(PartyErrorCode expected, Result<?, PartyErrorCode> result) {
        assertFalse(result.isSuccess());
        assertEquals(expected, ((Result.Failure<?, PartyErrorCode>) result).error());
    }
}
