package com.branz.mmorpg.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.player.DuplicateLoginPolicy;
import com.branz.mmorpg.api.skill.SkillDefinition;
import com.branz.mmorpg.core.fixture.DirectScheduler;
import com.branz.mmorpg.core.fixture.FakePlayerProfileRepository;
import com.branz.mmorpg.core.fixture.FixedGameClock;
import com.branz.mmorpg.core.player.PlayerSessionService;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DefaultLoadoutServiceTest {

    private static final UUID PLAYER =
            UUID.fromString("9a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");
    private static final ContentId WEAPON = ContentId.parse("branz:broadsword");

    @Test
    void equippedWeaponPersistsWithSessionAndCombatBlocksSwaps() throws Exception {
        FakePlayerProfileRepository repository = new FakePlayerProfileRepository();
        FixedGameClock clock = FixedGameClock.at("2026-07-26T00:00:00Z");
        PlayerSessionService sessions = new PlayerSessionService(
                repository, new DirectScheduler(), clock, () -> 1L,
                DuplicateLoginPolicy.CLOSE_PREVIOUS);
        sessions.start();
        try {
            sessions.login(PLAYER, "Branz").get();
            AtomicBoolean inCombat = new AtomicBoolean();
            WeaponDefinition weapon = weapon();
            DefaultLoadoutService loadouts = new DefaultLoadoutService(
                    sessions, () -> snapshot(weapon),
                    (ignored, now) -> inCombat.get(), clock);

            assertTrue(loadouts.equip(PLAYER, WEAPON).equipped());
            assertEquals(WEAPON, loadouts.current(PLAYER).orElseThrow().id());
            sessions.flushAll();
            assertEquals(WEAPON,
                    repository.stored(PLAYER).selectedLoadoutId().orElseThrow());

            inCombat.set(true);
            var blocked = loadouts.equip(PLAYER, WEAPON);
            assertFalse(blocked.equipped());
            assertEquals("loadout changes are blocked in combat", blocked.rejection());
        } finally {
            sessions.stop();
        }
    }

    private static WeaponDefinition weapon() {
        return new WeaponDefinition(WEAPON, "Broadsword",
                ContentId.parse("branz:sword"), ContentId.parse("branz:broadsword_mastery"),
                ContentId.parse("branz:basic_strike"), List.of(ContentId.parse("branz:heavy_slash")),
                Set.of("sword"), true);
    }

    private static ContentSnapshot snapshot(WeaponDefinition weapon) {
        return new ContentSnapshot() {
            @Override public long revision() { return 1; }
            @Override public Instant loadedAt() { return Instant.EPOCH; }
            @Override public Collection<ContentDefinition> definitions() {
                return List.of(weapon);
            }
            @Override public Optional<ContentDefinition> find(ContentId id) {
                return id.equals(weapon.id()) ? Optional.of(weapon) : Optional.empty();
            }
            @Override public <T extends ContentDefinition> Optional<T> find(
                    ContentId id, Class<T> type) {
                return find(id).filter(type::isInstance).map(type::cast);
            }
            @Override public Map<ContentId, MaterialDefinition> materials() { return Map.of(); }
            @Override public Map<ContentId, SkillDefinition> skills() { return Map.of(); }
            @Override public Map<ContentId, LifeSkillDefinition> lifeSkills() { return Map.of(); }
            @Override public Map<ContentId, LifeSkillNodeDefinition> lifeSkillNodes() {
                return Map.of();
            }
            @Override public Map<ContentId, MasteryDefinition> masteries() { return Map.of(); }
            @Override public Map<ContentId, WeaponDefinition> weapons() {
                return Map.of(weapon.id(), weapon);
            }
        };
    }
}
