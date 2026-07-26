package com.branz.mmorpg.api.lifeskill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LifeSkillProfileTest {

    private static final UUID PLAYER = UUID.fromString("9a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");
    private static final ContentId MINING = ContentId.parse("branz:mining");
    private static final ContentId FISHING = ContentId.parse("branz:fishing");
    private static final ContentId STONEWORKER = ContentId.parse("branz:mining_stoneworker");
    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    @Test
    void untrainedSkillIsAValueNotAnAbsence() {
        LifeSkillProfile profile = LifeSkillProfile.empty(PLAYER, NOW);

        LifeSkillSnapshot mining = profile.skill(MINING);

        assertNotNull(mining);
        assertEquals(MINING, mining.skillId());
        assertEquals(LifeSkillProgress.STARTING_LEVEL, mining.level());
        assertEquals(0L, mining.totalXp());
        assertFalse(mining.progress().started());
        assertTrue(profile.trainedSkills().isEmpty());
    }

    @Test
    void reportsTrainedSkillsAndRanks() {
        LifeSkillProfile profile = LifeSkillProfile.empty(PLAYER, NOW)
                .with(new LifeSkillSnapshot(
                        new LifeSkillProgress(MINING, 12, 4200L, 2, 7L, NOW),
                        Map.of(STONEWORKER, 2)));

        assertEquals(12, profile.level(MINING));
        assertEquals(2, profile.skill(MINING).rankOf(STONEWORKER));
        assertTrue(profile.hasNode(MINING, STONEWORKER, 2));
        assertFalse(profile.hasNode(MINING, STONEWORKER, 3));
        assertEquals(java.util.Set.of(MINING), profile.trainedSkills());

        // an untrained skill still answers, at rank 0
        assertEquals(0, profile.skill(FISHING).rankOf(STONEWORKER));
        assertFalse(profile.hasNode(FISHING, STONEWORKER, 1));
    }

    @Test
    void snapshotsAreDeeplyImmutable() {
        Map<ContentId, Integer> ranks = new HashMap<>();
        ranks.put(STONEWORKER, 1);
        LifeSkillSnapshot snapshot = new LifeSkillSnapshot(
                LifeSkillProgress.untrained(MINING, NOW), ranks);

        ranks.put(STONEWORKER, 99);

        assertEquals(1, snapshot.rankOf(STONEWORKER), "snapshot must not observe later edits");
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.nodeRanks().put(STONEWORKER, 99));
    }

    @Test
    void withReplacesOneSkillAndLeavesTheProfileUntouched() {
        LifeSkillProfile original = LifeSkillProfile.empty(PLAYER, NOW);

        LifeSkillProfile updated = original.with(new LifeSkillSnapshot(
                new LifeSkillProgress(MINING, 3, 300L, 1, 1L, NOW), Map.of()));

        assertTrue(original.trainedSkills().isEmpty(), "original snapshot is unchanged");
        assertEquals(3, updated.level(MINING));
        assertEquals(PLAYER, updated.playerId());
    }

    @Test
    void rejectsImpossibleProgress() {
        assertEquals(ErrorCode.INVALID_ARGUMENT,
                codeOf(() -> new LifeSkillProgress(MINING, 0, 0L, 0, 0L, NOW)));
        assertEquals(ErrorCode.INVALID_ARGUMENT,
                codeOf(() -> new LifeSkillProgress(MINING, 1, -1L, 0, 0L, NOW)));
        assertEquals(ErrorCode.INVALID_ARGUMENT,
                codeOf(() -> new LifeSkillProgress(MINING, 1, 0L, -1, 0L, NOW)));
        assertEquals(ErrorCode.INVALID_ARGUMENT,
                codeOf(() -> new LifeSkillSnapshot(
                        LifeSkillProgress.untrained(MINING, NOW), Map.of(STONEWORKER, 0))));
    }

    @Test
    void queryDefaultsDelegateToTheProfile() {
        LifeSkillProfile profile = LifeSkillProfile.empty(PLAYER, NOW)
                .with(new LifeSkillSnapshot(
                        new LifeSkillProgress(MINING, 9, 1000L, 0, 1L, NOW),
                        Map.of(STONEWORKER, 3)));
        LifeSkillQuery query = playerId -> {
            if (!PLAYER.equals(playerId)) {
                throw new MMOException(ErrorCode.PROFILE_LOAD_FAILED, "not loaded: " + playerId);
            }
            return profile;
        };

        assertEquals(9, query.level(PLAYER, MINING));
        assertTrue(query.hasNode(PLAYER, MINING, STONEWORKER, 3));
        assertEquals(ErrorCode.PROFILE_LOAD_FAILED,
                codeOf(() -> query.profile(UUID.randomUUID())));
    }

    private static ErrorCode codeOf(Runnable work) {
        return assertThrows(MMOException.class, work::run).code();
    }
}
