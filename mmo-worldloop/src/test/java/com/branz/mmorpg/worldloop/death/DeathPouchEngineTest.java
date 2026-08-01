package com.branz.mmorpg.worldloop.death;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeathPouchEngineTest {
    private final DeathPouchEngine engine = new DeathPouchEngine();
    private final CharacterId owner = new CharacterId(UUID.randomUUID());
    private final DeathPouchLocation location =
            new DeathPouchLocation("minecraft:overworld", 10.5, 64, -3.25);

    @Test
    void openWorldDeathPlansTenPercentWithStableSagaIdsAndSevenDayExpiry() {
        UUID deathId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-01T12:00:00Z");
        DeathPouchDecision first =
                engine.plan(
                        deathId,
                        owner,
                        DeathPouchContext.OPEN_WORLD_PVE,
                        1_009,
                        location,
                        createdAt);
        DeathPouchDecision replay =
                engine.plan(
                        deathId,
                        owner,
                        DeathPouchContext.OPEN_WORLD_PVE,
                        1_009,
                        location,
                        createdAt);

        assertEquals(first, replay);
        DeathPouchDraft draft = first.draft().orElseThrow();
        assertEquals(100, draft.amount());
        assertEquals(createdAt.plus(7, ChronoUnit.DAYS), draft.expiresAt());
        assertEquals(DeathPouchDecisionReason.POUCH_PLANNED, first.reason());
    }

    @Test
    void repeatedDeathsUseRemainingWalletAndNeverMergeIdentities() {
        Instant now = Instant.EPOCH;
        DeathPouchDraft first =
                engine.plan(
                                UUID.randomUUID(),
                                owner,
                                DeathPouchContext.OPEN_WORLD_PVE,
                                1_000,
                                location,
                                now)
                        .draft()
                        .orElseThrow();
        DeathPouchDraft second =
                engine.plan(
                                UUID.randomUUID(),
                                owner,
                                DeathPouchContext.OPEN_WORLD_PVE,
                                900,
                                location,
                                now.plusSeconds(1))
                        .draft()
                        .orElseThrow();
        assertEquals(100, first.amount());
        assertEquals(90, second.amount());
        assertTrue(!first.pouchId().equals(second.pouchId()));
    }

    @Test
    void bossPvpAndSubTenUnitWalletsCreateNoSpendableDraft() {
        assertEquals(
                DeathPouchDecisionReason.BOSS_PROFILE_SUPPRESSED,
                plan(DeathPouchContext.BOSS_SUPPRESSED, 1_000).reason());
        assertEquals(
                DeathPouchDecisionReason.PVP_PROFILE_SUPPRESSED,
                plan(DeathPouchContext.DUEL, 1_000).reason());
        assertEquals(
                DeathPouchDecisionReason.PVP_PROFILE_SUPPRESSED,
                plan(DeathPouchContext.ARENA, 1_000).reason());
        assertEquals(
                DeathPouchDecisionReason.CARRIED_WALLET_TOO_SMALL,
                plan(DeathPouchContext.OPEN_WORLD_PVE, 9).reason());
    }

    @Test
    void invalidWalletAndLocationFailClosed() {
        assertThrows(
                IllegalArgumentException.class, () -> plan(DeathPouchContext.OPEN_WORLD_PVE, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DeathPouchLocation("minecraft:overworld", Double.NaN, 64, 0));
    }

    private DeathPouchDecision plan(DeathPouchContext context, long wallet) {
        return engine.plan(UUID.randomUUID(), owner, context, wallet, location, Instant.EPOCH);
    }
}
