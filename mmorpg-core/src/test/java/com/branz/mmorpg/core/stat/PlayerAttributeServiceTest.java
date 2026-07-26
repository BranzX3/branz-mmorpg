package com.branz.mmorpg.core.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.character.CharacterClassId;
import com.branz.mmorpg.api.character.CharacterClassRole;
import com.branz.mmorpg.api.character.StarterGrantPlan;
import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentReloadResult;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.player.DuplicateLoginPolicy;
import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.skill.ResourceType;
import com.branz.mmorpg.api.skill.SkillDefinition;
import com.branz.mmorpg.api.stat.AttributeChanged;
import com.branz.mmorpg.api.stat.AttributeModifier;
import com.branz.mmorpg.api.stat.AttributeType;
import com.branz.mmorpg.api.stat.ModifierAdded;
import com.branz.mmorpg.api.stat.ModifierSource;
import com.branz.mmorpg.api.stat.ResourceChanged;
import com.branz.mmorpg.api.stat.ResourceDepleted;
import com.branz.mmorpg.core.event.SimpleEventBus;
import com.branz.mmorpg.core.fixture.DirectScheduler;
import com.branz.mmorpg.core.fixture.FakePlayerProfileRepository;
import com.branz.mmorpg.core.fixture.FixedGameClock;
import com.branz.mmorpg.core.player.PlayerSessionService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerAttributeServiceTest {
    private static final UUID PLAYER = UUID.fromString("561e67a6-b900-4bb4-a6b8-75ab77a71b31");
    private FixedGameClock clock;
    private FakePlayerProfileRepository repository;
    private PlayerSessionService sessions;
    private SimpleEventBus events;
    private PlayerAttributeService service;

    @BeforeEach
    void setUp() throws Exception {
        clock = FixedGameClock.at("2026-07-26T12:00:00Z");
        repository = new FakePlayerProfileRepository();
        PlayerProfile profile = PlayerProfile.create(PLAYER, "Stats", clock.now())
                .withPermanentClass(CharacterClassId.WARRIOR.value());
        repository.preload(profile);
        sessions = new PlayerSessionService(repository, new DirectScheduler(), clock,
                () -> 41L, DuplicateLoginPolicy.CLOSE_PREVIOUS);
        sessions.start();
        sessions.login(PLAYER, "Stats").get();
        events = new SimpleEventBus();
        service = new PlayerAttributeService(sessions, new FixedContent(41L, warrior()), events, clock);
    }

    @AfterEach void stop() { sessions.stop(); }

    @Test
    void permanentClassBuildsTheCorrectBaseStatsAndResourcePolicies() {
        PlayerStatBlock block = service.activate(PLAYER);

        assertEquals(140.0, service.attributes(PLAYER).get(AttributeType.MAX_HEALTH), 1e-9);
        assertEquals(100.0, service.resource(PLAYER, ResourceType.STAMINA).current(), 1e-9);
        assertEquals(0.0, service.resource(PLAYER, ResourceType.RAGE).current(), 1e-9,
                "Warrior Rage starts empty");

        assertFalse(service.spend(PLAYER,
                Map.of(ResourceType.STAMINA, 20.0, ResourceType.RAGE, 1.0), "combo"));
        assertEquals(100.0, service.resource(PLAYER, ResourceType.STAMINA).current(), 1e-9,
                "multi-resource spending is atomic");

        service.add(PLAYER, ResourceType.RAGE, 25.0, "valid_hit");
        service.tick(PLAYER, 200, false);
        assertEquals(25.0, service.resource(PLAYER, ResourceType.RAGE).current(), 1e-9,
                "Rage is generated, never passively regenerated");
        assertEquals(ResourceType.STAMINA, block.primaryResource());
    }

    @Test
    void duplicateModifierCannotStackAndChangesArePublishedOnce() {
        List<ModifierAdded> added = new java.util.ArrayList<>();
        List<AttributeChanged> attributes = new java.util.ArrayList<>();
        List<ResourceChanged> resources = new java.util.ArrayList<>();
        List<ResourceDepleted> depleted = new java.util.ArrayList<>();
        events.subscribe(ModifierAdded.class, added::add);
        events.subscribe(AttributeChanged.class, attributes::add);
        events.subscribe(ResourceChanged.class, resources::add);
        events.subscribe(ResourceDepleted.class, depleted::add);
        service.activate(PLAYER);
        AttributeModifier wound = AttributeModifier.flat("wound", AttributeType.MAX_HEALTH, -100,
                ModifierSource.of(ModifierSource.SourceType.STATUS, "branz:wound"));

        assertTrue(service.addModifier(PLAYER, wound));
        assertFalse(service.addModifier(PLAYER, wound));

        assertEquals(1, added.size());
        assertEquals(1, attributes.stream()
                .filter(event -> event.attribute() == AttributeType.MAX_HEALTH).count());
        assertEquals(40.0, service.resource(PLAYER, ResourceType.HEALTH).maximum(), 1e-9);
        assertEquals(40.0, service.resource(PLAYER, ResourceType.HEALTH).current(), 1e-9);
        assertEquals(1, resources.size(), "maximum clamp is a coalesced resource change");

        assertTrue(service.spend(PLAYER, Map.of(ResourceType.HEALTH, 40.0), "damage"));
        assertEquals(1, depleted.size());
    }

    @Test
    void unselectedProfileCannotActivateCombatStats() throws Exception {
        UUID unselected = UUID.randomUUID();
        sessions.login(unselected, "PreviewOnly").get();

        assertThrows(MMOException.class, () -> service.activate(unselected));
        assertTrue(service.find(unselected).isEmpty());
    }

    private static CharacterClassDefinition warrior() {
        StarterGrantPlan starter = new StarterGrantPlan(ContentId.parse("branz:warrior_starter"),
                1, ContentId.parse("branz:broadsword"),
                List.of(ContentId.parse("branz:basic_strike")), Map.of());
        return new CharacterClassDefinition(CharacterClassId.WARRIOR.value(), "Warrior", 1,
                Set.of(CharacterClassRole.DAMAGE, CharacterClassRole.TANK),
                Map.of("max_health", 140.0, "max_stamina", 100.0, "max_rage", 100.0,
                        "physical_power", 18.0),
                ResourceType.STAMINA, Set.of(ResourceType.RAGE), Set.of("sword"),
                Set.of("heavy"), List.of(ContentId.parse("branz:heavy_slash")),
                ContentId.parse("branz:heavy_slash"), ContentId.parse("branz:warrior_root"),
                starter, Set.of("physical"));
    }

    private record FixedContent(long revision, CharacterClassDefinition definition)
            implements ContentService, ContentSnapshot {
        @Override public ContentSnapshot snapshot() { return this; }
        @Override public ContentReloadResult reload(Path root) { throw new UnsupportedOperationException(); }
        @Override public Instant loadedAt() { return Instant.EPOCH; }
        @Override public Collection<ContentDefinition> definitions() { return List.of(definition); }
        @Override public Optional<ContentDefinition> find(ContentId id) {
            return definition.id().equals(id) ? Optional.of(definition) : Optional.empty();
        }
        @Override public <T extends ContentDefinition> Optional<T> find(ContentId id, Class<T> type) {
            return find(id).filter(type::isInstance).map(type::cast);
        }
        @Override public Map<ContentId, MaterialDefinition> materials() { return Map.of(); }
        @Override public Map<ContentId, SkillDefinition> skills() { return Map.of(); }
        @Override public Map<ContentId, LifeSkillDefinition> lifeSkills() { return Map.of(); }
        @Override public Map<ContentId, LifeSkillNodeDefinition> lifeSkillNodes() { return Map.of(); }
        @Override public Map<ContentId, MasteryDefinition> masteries() { return Map.of(); }
        @Override public Map<ContentId, WeaponDefinition> weapons() { return Map.of(); }
        @Override public Map<ContentId, CharacterClassDefinition> characterClasses() {
            return Map.of(definition.id(), definition);
        }
    }
}
