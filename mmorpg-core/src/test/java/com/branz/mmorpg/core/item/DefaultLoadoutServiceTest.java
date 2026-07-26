package com.branz.mmorpg.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.character.CharacterClassId;
import com.branz.mmorpg.api.character.CharacterClassRole;
import com.branz.mmorpg.api.character.StarterGrantPlan;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.player.DuplicateLoginPolicy;
import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.skill.ResourceType;
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
            repository.preload(PlayerProfile.create(PLAYER, "Branz", clock.now())
                    .withPermanentClass(CharacterClassId.WARRIOR.value()));
            sessions.login(PLAYER, "Branz").get();
            AtomicBoolean inCombat = new AtomicBoolean();
            WeaponDefinition weapon = weapon();
            DefaultLoadoutService loadouts = new DefaultLoadoutService(
                    sessions, () -> snapshot(weapon),
                    (ignored, now) -> inCombat.get(), clock);

            long beforeEquip = loadouts.revision(PLAYER);
            assertTrue(loadouts.equip(PLAYER, WEAPON).equipped());
            long equippedRevision = loadouts.revision(PLAYER);
            assertTrue(equippedRevision > beforeEquip);
            assertTrue(loadouts.equip(PLAYER, WEAPON).equipped());
            assertEquals(equippedRevision, loadouts.revision(PLAYER));
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

    @Test
    void crossClassWeaponFailsWithoutChangingTheLastValidLoadout() throws Exception {
        FakePlayerProfileRepository repository = new FakePlayerProfileRepository();
        FixedGameClock clock = FixedGameClock.at("2026-07-26T00:00:00Z");
        repository.preload(PlayerProfile.create(PLAYER, "Branz", clock.now())
                .withPermanentClass(CharacterClassId.WARRIOR.value()));
        PlayerSessionService sessions = new PlayerSessionService(repository, new DirectScheduler(),
                clock, () -> 1L, DuplicateLoginPolicy.CLOSE_PREVIOUS);
        sessions.start();
        try {
            sessions.login(PLAYER, "Branz").get();
            WeaponDefinition sword = weapon();
            WeaponDefinition staff = new WeaponDefinition(ContentId.parse("branz:staff"), "Staff",
                    ContentId.parse("branz:magic"), ContentId.parse("branz:staff_mastery"),
                    ContentId.parse("branz:basic_strike"), List.of(), Set.of("staff"), true);
            ContentSnapshot snapshot = snapshot(sword, staff);
            DefaultLoadoutService loadouts = new DefaultLoadoutService(sessions, () -> snapshot,
                    (ignored, now) -> false, clock);
            assertTrue(loadouts.equip(PLAYER, sword.id()).equipped());
            long validRevision = loadouts.revision(PLAYER);

            var rejected = loadouts.equip(PLAYER, staff.id());

            assertFalse(rejected.equipped());
            assertTrue(rejected.rejection().contains("incompatible"));
            assertEquals(validRevision, loadouts.revision(PLAYER));
            assertEquals(sword.id(), loadouts.current(PLAYER).orElseThrow().id());
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

    private static ContentSnapshot snapshot(WeaponDefinition... supplied) {
        Map<ContentId, WeaponDefinition> weapons = java.util.Arrays.stream(supplied)
                .collect(java.util.stream.Collectors.toMap(WeaponDefinition::id, value -> value));
        CharacterClassDefinition warrior = new CharacterClassDefinition(
                CharacterClassId.WARRIOR.value(), "Warrior", 1,
                Set.of(CharacterClassRole.DAMAGE), Map.of("max_health", 100.0,
                        "max_stamina", 100.0), ResourceType.STAMINA, Set.of(),
                Set.of("sword"), Set.of("heavy"), List.of(ContentId.parse("branz:slash")),
                ContentId.parse("branz:ultimate"), ContentId.parse("branz:root"),
                new StarterGrantPlan(ContentId.parse("branz:starter"), 1, WEAPON,
                        List.of(ContentId.parse("branz:basic_strike")), Map.of()), Set.of());
        return new ContentSnapshot() {
            @Override public long revision() { return 1; }
            @Override public Instant loadedAt() { return Instant.EPOCH; }
            @Override public Collection<ContentDefinition> definitions() {
                return new java.util.ArrayList<>(weapons.values());
            }
            @Override public Optional<ContentDefinition> find(ContentId id) {
                if (id.equals(warrior.id())) return Optional.of(warrior);
                return Optional.ofNullable(weapons.get(id));
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
                return weapons;
            }
            @Override public Map<ContentId, CharacterClassDefinition> characterClasses() {
                return Map.of(warrior.id(), warrior);
            }
        };
    }
}
