package com.branz.mmorpg.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.character.CharacterClassId;
import com.branz.mmorpg.api.character.CharacterClassRole;
import com.branz.mmorpg.api.character.CharacterClassSelected;
import com.branz.mmorpg.api.character.CharacterClassSelectionRepository;
import com.branz.mmorpg.api.character.CharacterClassSelectionRequest;
import com.branz.mmorpg.api.character.CharacterClassSelectionResult;
import com.branz.mmorpg.api.character.CharacterClassSnapshot;
import com.branz.mmorpg.api.character.CharacterClassState;
import com.branz.mmorpg.api.character.StarterGrantPlan;
import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentReloadResult;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.player.DuplicateLoginPolicy;
import com.branz.mmorpg.api.player.PlayerSession;
import com.branz.mmorpg.api.skill.ResourceType;
import com.branz.mmorpg.api.skill.SkillDefinition;
import com.branz.mmorpg.core.event.SimpleEventBus;
import com.branz.mmorpg.core.fixture.DirectScheduler;
import com.branz.mmorpg.core.fixture.FakePlayerProfileRepository;
import com.branz.mmorpg.core.fixture.FixedGameClock;
import com.branz.mmorpg.core.player.PlayerSessionService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PermanentCharacterClassServiceTest {
    private static final UUID PLAYER = UUID.fromString("ba41f41e-a504-4d65-bfc4-fdf1a861a327");
    private static final long CONTENT_REVISION = 31;
    private PlayerSessionService sessions;
    private PlayerSession session;
    private InMemorySelections selections;
    private PermanentCharacterClassService service;
    private SimpleEventBus events;

    @BeforeEach
    void setUp() throws Exception {
        FixedGameClock clock = FixedGameClock.at("2026-07-26T09:00:00Z");
        sessions = new PlayerSessionService(new FakePlayerProfileRepository(),
                new DirectScheduler(), clock, () -> CONTENT_REVISION,
                DuplicateLoginPolicy.CLOSE_PREVIOUS);
        sessions.start();
        session = sessions.login(PLAYER, "ClassTester").get();
        selections = new InMemorySelections();
        events = new SimpleEventBus();
        service = new PermanentCharacterClassService(sessions,
                new FixedContent(CONTENT_REVISION, warrior()), selections, events, clock);
    }

    @AfterEach void stop() { sessions.stop(); }

    @Test
    void commitsOnceAndReturnsTheOriginalResultForARetry() {
        List<CharacterClassSelected> published = new java.util.ArrayList<>();
        events.subscribe(CharacterClassSelected.class, published::add);
        CharacterClassSelectionRequest request = request("choose-warrior", true,
                session.profile().revision(), CONTENT_REVISION);

        CharacterClassSelectionResult first = service.select(request);
        CharacterClassSelectionResult retry = service.select(request);

        assertTrue(first.applied());
        assertFalse(retry.applied());
        assertEquals(first.snapshot(), retry.snapshot());
        assertEquals(first.starterGrantPlan(), retry.starterGrantPlan());
        assertEquals(CharacterClassId.WARRIOR.value(), session.profile().classId().orElseThrow());
        assertEquals(1, selections.commitCount);
        assertEquals(1, published.size(), "a replay must not publish or grant again");
    }

    @Test
    void rejectsASecondPermanentChoiceWithAnotherOperation() {
        service.select(request("first", true, 0, CONTENT_REVISION));
        CharacterClassSelectionRequest second = request("second", true,
                session.profile().revision(), CONTENT_REVISION);

        MMOException failure = assertThrows(MMOException.class, () -> service.select(second));

        assertEquals(ErrorCode.INVALID_ARGUMENT, failure.code());
        assertEquals(1, selections.commitCount);
    }

    @Test
    void requiresConfirmationAndCurrentRevisions() {
        MMOException confirmation = assertThrows(MMOException.class,
                () -> service.select(request("no-confirm", false, 0, CONTENT_REVISION)));
        MMOException content = assertThrows(MMOException.class,
                () -> service.select(request("stale-content", true, 0, CONTENT_REVISION - 1)));
        MMOException profile = assertThrows(MMOException.class,
                () -> service.select(request("stale-profile", true, 5, CONTENT_REVISION)));

        assertEquals(ErrorCode.INVALID_ARGUMENT, confirmation.code());
        assertEquals(ErrorCode.CONTENT_INVALID, content.code());
        assertEquals(ErrorCode.INVALID_ARGUMENT, profile.code());
        assertEquals(0, selections.commitCount);
    }

    private CharacterClassSelectionRequest request(
            String discriminator, boolean confirmed, long profileRevision, long contentRevision) {
        return new CharacterClassSelectionRequest(
                OperationId.of("class", "selection", PLAYER, discriminator), PLAYER,
                session.token(), CharacterClassId.WARRIOR, profileRevision, contentRevision, confirmed);
    }

    private static CharacterClassDefinition warrior() {
        StarterGrantPlan starter = new StarterGrantPlan(ContentId.parse("branz:warrior_starter"),
                1, ContentId.parse("branz:broadsword"),
                List.of(ContentId.parse("branz:basic_strike")), Map.of());
        return new CharacterClassDefinition(CharacterClassId.WARRIOR.value(), "Warrior", 1,
                Set.of(CharacterClassRole.DAMAGE, CharacterClassRole.TANK),
                Map.of("strength", 12.0), ResourceType.STAMINA, Set.of("sword"),
                Set.of("heavy"), List.of(ContentId.parse("branz:heavy_slash")),
                ContentId.parse("branz:heavy_slash"), ContentId.parse("branz:warrior_root"),
                starter, Set.of("physical"));
    }

    private static final class InMemorySelections implements CharacterClassSelectionRepository {
        private final Map<UUID, CharacterClassSelectionResult> stored = new LinkedHashMap<>();
        int commitCount;

        @Override public Optional<CharacterClassSelectionResult> find(UUID playerId, OperationId operationId) {
            CharacterClassSelectionResult result = stored.get(playerId);
            if (result == null) return Optional.empty();
            if (!result.snapshot().selectionOperationId().orElseThrow().equals(operationId)) {
                return Optional.empty();
            }
            return Optional.of(new CharacterClassSelectionResult(
                    CharacterClassSelectionResult.Status.REPLAYED, result.snapshot(),
                    result.starterGrantPlan(), result.contentRevision()));
        }

        @Override public CharacterClassSelectionResult select(
                UUID playerId, long expectedProfileRevision, OperationId operationId,
                CharacterClassDefinition definition, long contentRevision, Instant selectedAt) {
            CharacterClassSelectionResult prior = stored.get(playerId);
            if (prior != null) {
                if (prior.snapshot().selectionOperationId().orElseThrow().equals(operationId)) {
                    return new CharacterClassSelectionResult(CharacterClassSelectionResult.Status.REPLAYED,
                            prior.snapshot(), prior.starterGrantPlan(), prior.contentRevision());
                }
                throw new MMOException(ErrorCode.INVALID_ARGUMENT, "class already selected");
            }
            commitCount++;
            CharacterClassSnapshot snapshot = new CharacterClassSnapshot(playerId,
                    CharacterClassState.CLASS_SELECTED, Optional.of(definition.classId()),
                    Optional.of(selectedAt), Optional.of(operationId), definition.schemaVersion(),
                    expectedProfileRevision + 1);
            CharacterClassSelectionResult result = new CharacterClassSelectionResult(
                    CharacterClassSelectionResult.Status.APPLIED, snapshot,
                    definition.starterGrantPlan(), contentRevision);
            stored.put(playerId, result);
            return result;
        }
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
