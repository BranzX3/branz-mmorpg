package com.branz.mmorpg.core.lifeskill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.player.DuplicateLoginPolicy;
import com.branz.mmorpg.api.skill.SkillDefinition;
import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.core.fixture.DirectScheduler;
import com.branz.mmorpg.core.fixture.FakePlayerProfileRepository;
import com.branz.mmorpg.core.fixture.FixedGameClock;
import com.branz.mmorpg.core.player.PlayerSessionService;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LifeSkillProgressionServiceTest {

    @Test
    void repeatedOperationIdCommitsXpExactlyOnceAndUpdatesTheSession() throws Exception {
        UUID player = UUID.fromString("9a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");
        ContentId mining = ContentId.parse("branz:mining");
        FakePlayerProfileRepository repository = new FakePlayerProfileRepository();
        FixedGameClock clock = FixedGameClock.at("2026-07-26T12:00:00Z");
        PlayerSessionService sessions = new PlayerSessionService(repository, new DirectScheduler(),
                clock, () -> 5L, DuplicateLoginPolicy.CLOSE_PREVIOUS);
        sessions.start();
        sessions.login(player, "Branz").get();
        LifeSkillProgressionService progression = new LifeSkillProgressionService(
                repository, sessions, clock, () -> snapshot(mining));
        OperationId operation = OperationId.of("mastery", mining.toString(), player, "harvest_1");

        var first = progression.grantXp(player, mining, 100L, operation);
        var repeated = progression.grantXp(player, mining, 100L, operation);

        assertTrue(first.applied());
        assertFalse(repeated.applied());
        assertEquals(100L, sessions.profile(player).skill(mining).totalXp());
        assertEquals(100L, repository.storedLifeSkills(player).skill(mining).totalXp());
        sessions.stop();
    }

    private static ContentSnapshot snapshot(ContentId mining) {
        LifeSkillDefinition definition =
                new LifeSkillDefinition(mining, "Mining", 100, 75, 1.55, Set.of(2, 5));
        Map<ContentId, ContentDefinition> definitions = Map.of(mining, definition);
        return new ContentSnapshot() {
            @Override public long revision() { return 5L; }
            @Override public Instant loadedAt() { return Instant.EPOCH; }
            @Override public Collection<ContentDefinition> definitions() {
                return definitions.values();
            }
            @Override public Optional<ContentDefinition> find(ContentId id) {
                return Optional.ofNullable(definitions.get(id));
            }
            @Override public <T extends ContentDefinition> Optional<T> find(ContentId id, Class<T> type) {
                return find(id).filter(type::isInstance).map(type::cast);
            }
            @Override public Map<ContentId, MaterialDefinition> materials() { return Map.of(); }
            @Override public Map<ContentId, SkillDefinition> skills() { return Map.of(); }
            @Override public Map<ContentId, LifeSkillDefinition> lifeSkills() {
                return Map.of(mining, definition);
            }
            @Override public Map<ContentId, LifeSkillNodeDefinition> lifeSkillNodes() {
                return Map.of();
            }
            @Override public Map<ContentId, MasteryDefinition> masteries() { return Map.of(); }
            @Override public Map<ContentId, WeaponDefinition> weapons() { return Map.of(); }
        };
    }
}
