package com.branz.mmorpg.social.pvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PvpMatchEngineTest {
    private final PvpMatchEngine engine = new PvpMatchEngine();
    private final CharacterId alpha = character();
    private final CharacterId beta = character();
    private final PvpCombatProfile profile = PvpCombatProfile.canonical();

    @Test
    void duelRequiresConsentAndCountdownBeforeHostilePermission() {
        PvpMatchRuntime challenged = challenge(alpha, beta, 10);
        assertEquals(PvpMatchPhase.CHALLENGED, challenged.phase());
        assertFalse(engine.hostileAllowed(challenged, alpha, beta));

        PvpMatchRuntime countdown =
                success(engine.accept(challenged, beta, UUID.randomUUID(), 20)).runtime();
        assertEquals(PvpMatchPhase.COUNTDOWN, countdown.phase());
        assertEquals(120, countdown.phaseEndsTick());
        assertFalse(engine.hostileAllowed(countdown, alpha, beta));
        assertFalse(success(engine.advance(countdown, UUID.randomUUID(), 119)).changed());

        PvpTransition started = success(engine.advance(countdown, UUID.randomUUID(), 120));
        assertTrue(started.newlyActive());
        assertTrue(engine.hostileAllowed(started.runtime(), alpha, beta));
        assertTrue(engine.hostileAllowed(started.runtime(), beta, alpha));
        assertFalse(engine.hostileAllowed(started.runtime(), alpha, alpha));
    }

    @Test
    void duelDefeatIsTerminalAndOperationReplayCannotDuplicateEffects() {
        PvpMatchRuntime active = duelActive();
        UUID defeatOperation = UUID.randomUUID();

        PvpTransition defeated = success(engine.defeat(active, beta, defeatOperation));
        assertEquals(PvpMatchPhase.COMPLETED, defeated.runtime().phase());
        assertEquals(PvpCompletionReason.DEFEAT, defeated.completion().orElseThrow().reason());
        assertEquals(0, defeated.completion().orElseThrow().winningTeam().orElseThrow());
        assertEquals(java.util.Set.of(beta), defeated.newlyDefeated());
        assertFalse(profile.durabilityLossAllowed());
        assertFalse(profile.deathPouchAllowed());

        PvpTransition replay = success(engine.defeat(defeated.runtime(), beta, defeatOperation));
        assertFalse(replay.changed());
        assertEquals(
                PvpErrorCode.OPERATION_ID_REUSED,
                failure(engine.surrender(defeated.runtime(), beta, defeatOperation)));
    }

    @Test
    void challengeAdmissionDeclineAndExpiryFailClosed() {
        Result<PvpTransition, PvpErrorCode> unsafe =
                engine.challengeDuel(
                        match(),
                        alpha,
                        beta,
                        PvpAdmission.eligible(),
                        new PvpAdmission(true, false, false, false),
                        profile,
                        UUID.randomUUID(),
                        0);
        assertEquals(PvpErrorCode.ADMISSION_REJECTED, failure(unsafe));
        assertEquals(
                PvpErrorCode.PARTICIPANT_INVALID,
                failure(
                        engine.challengeDuel(
                                match(),
                                alpha,
                                alpha,
                                PvpAdmission.eligible(),
                                PvpAdmission.eligible(),
                                profile,
                                UUID.randomUUID(),
                                0)));

        PvpMatchRuntime challenged = challenge(alpha, beta, 0);
        assertEquals(
                PvpCompletionReason.DECLINED,
                success(engine.decline(challenged, beta, UUID.randomUUID()))
                        .completion()
                        .orElseThrow()
                        .reason());
        assertEquals(
                PvpCompletionReason.CHALLENGE_EXPIRED,
                success(
                                engine.advance(
                                        challenge(alpha, beta, 0),
                                        UUID.randomUUID(),
                                        PvpMatchEngine.CHALLENGE_DURATION_TICKS))
                        .completion()
                        .orElseThrow()
                        .reason());
    }

    @Test
    void surrenderBoundaryAndDisconnectTimeoutAwardTheOpposingTeam() {
        PvpTransition surrendered =
                success(engine.surrender(duelActive(), alpha, UUID.randomUUID()));
        assertEquals(
                PvpCompletionReason.SURRENDER, surrendered.completion().orElseThrow().reason());
        assertEquals(1, surrendered.completion().orElseThrow().winningTeam().orElseThrow());

        PvpTransition boundary =
                success(engine.boundaryForfeit(duelActive(), beta, UUID.randomUUID()));
        assertEquals(
                PvpCompletionReason.BOUNDARY_FORFEIT, boundary.completion().orElseThrow().reason());
        assertEquals(0, boundary.completion().orElseThrow().winningTeam().orElseThrow());

        PvpMatchRuntime active = duelActive();
        PvpMatchRuntime disconnected =
                success(engine.disconnect(active, beta, UUID.randomUUID(), 200)).runtime();
        PvpMatchRuntime reconnected =
                success(engine.reconnect(disconnected, beta, UUID.randomUUID(), 399)).runtime();
        assertEquals(PvpParticipantStatus.READY, reconnected.participants().get(beta).status());
        PvpMatchRuntime disconnectedAgain =
                success(engine.disconnect(reconnected, beta, UUID.randomUUID(), 500)).runtime();
        PvpTransition timedOut = success(engine.advance(disconnectedAgain, UUID.randomUUID(), 700));
        assertEquals(
                PvpCompletionReason.DISCONNECT_TIMEOUT,
                timedOut.completion().orElseThrow().reason());
        assertEquals(0, timedOut.completion().orElseThrow().winningTeam().orElseThrow());
    }

    @Test
    void arenaRemainsActiveUntilOneWholeTeamIsDefeated() {
        CharacterId ally = character();
        Map<CharacterId, Integer> teams = Map.of(alpha, 0, ally, 0, beta, 1);
        Map<CharacterId, PvpAdmission> admissions =
                Map.of(
                        alpha,
                        PvpAdmission.eligible(),
                        ally,
                        PvpAdmission.eligible(),
                        beta,
                        PvpAdmission.eligible());
        PvpMatchRuntime countdown =
                success(
                                engine.startArena(
                                        match(),
                                        alpha,
                                        teams,
                                        admissions,
                                        profile,
                                        UUID.randomUUID(),
                                        0))
                        .runtime();
        PvpMatchRuntime active =
                success(
                                engine.advance(
                                        countdown,
                                        UUID.randomUUID(),
                                        PvpMatchEngine.COUNTDOWN_TICKS))
                        .runtime();

        PvpTransition first = success(engine.defeat(active, alpha, UUID.randomUUID()));
        assertEquals(PvpMatchPhase.ACTIVE, first.runtime().phase());
        assertTrue(first.completion().isEmpty());
        assertFalse(engine.hostileAllowed(first.runtime(), alpha, beta));
        assertTrue(engine.hostileAllowed(first.runtime(), ally, beta));

        PvpTransition terminal = success(engine.defeat(first.runtime(), ally, UUID.randomUUID()));
        assertEquals(PvpMatchPhase.COMPLETED, terminal.runtime().phase());
        assertEquals(1, terminal.completion().orElseThrow().winningTeam().orElseThrow());
        assertEquals(java.util.Set.of(alpha, ally), terminal.completion().orElseThrow().defeated());
    }

    @Test
    void profileAndRuntimeRejectInvalidValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PvpCombatProfile(0, 1, 1, 1, 30, true, false));
        assertEquals(0.70, profile.damageMultiplier());
        assertEquals(0.60, profile.healingMultiplier());
        assertEquals(0.85, profile.guardPressureMultiplier());
        assertEquals(0.65, profile.ccDurationMultiplier());
        assertEquals(30, profile.hardCcImmunityTicks());
        assertTrue(profile.flaskAllowed());
        assertFalse(profile.externalBuffsAllowed());

        assertEquals(
                PvpErrorCode.TEAM_INVALID,
                failure(
                        engine.startArena(
                                match(),
                                alpha,
                                Map.of(alpha, 0, beta, 0),
                                Map.of(
                                        alpha,
                                        PvpAdmission.eligible(),
                                        beta,
                                        PvpAdmission.eligible()),
                                profile,
                                UUID.randomUUID(),
                                0)));
    }

    private PvpMatchRuntime duelActive() {
        PvpMatchRuntime challenged = challenge(alpha, beta, 0);
        PvpMatchRuntime countdown =
                success(engine.accept(challenged, beta, UUID.randomUUID(), 1)).runtime();
        return success(
                        engine.advance(
                                countdown, UUID.randomUUID(), 1 + PvpMatchEngine.COUNTDOWN_TICKS))
                .runtime();
    }

    private PvpMatchRuntime challenge(CharacterId challenger, CharacterId target, long tick) {
        return success(
                        engine.challengeDuel(
                                match(),
                                challenger,
                                target,
                                PvpAdmission.eligible(),
                                PvpAdmission.eligible(),
                                profile,
                                UUID.randomUUID(),
                                tick))
                .runtime();
    }

    private static EncounterId match() {
        return new EncounterId(UUID.randomUUID());
    }

    private static CharacterId character() {
        return new CharacterId(UUID.randomUUID());
    }

    private static <T> T success(Result<T, PvpErrorCode> result) {
        assertTrue(result.isSuccess(), () -> failureDetail(result));
        return ((Result.Success<T, PvpErrorCode>) result).value();
    }

    private static PvpErrorCode failure(Result<?, PvpErrorCode> result) {
        assertFalse(result.isSuccess());
        return ((Result.Failure<?, PvpErrorCode>) result).error();
    }

    private static String failureDetail(Result<?, PvpErrorCode> result) {
        if (result instanceof Result.Failure<?, PvpErrorCode> failure) {
            return failure.error() + ": " + failure.detail();
        }
        return "";
    }
}
