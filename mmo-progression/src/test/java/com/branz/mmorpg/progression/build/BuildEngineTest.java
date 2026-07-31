package com.branz.mmorpg.progression.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.snapshot.ContentLoadFailure;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.content.snapshot.ContentSnapshotLoader;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuildEngineTest {
    private static final DefinitionId STAFF_TECHNIQUE = DefinitionId.of("technique.staff.sweep");
    private static final DefinitionId EMBER_FORM = DefinitionId.of("form.ember_channel");
    private static final DefinitionId FIRE_LANCE = DefinitionId.of("spell.ember.fire_lance");

    @Test
    void compilesFixtureAndResolvesTechniqueFormAndAttunementLoad() {
        BuildEngine engine = engine();
        CharacterBuild build =
                new CharacterBuild(
                        Map.of(MovesetBranch.PRIMARY_1, STAFF_TECHNIQUE),
                        Optional.of(EMBER_FORM),
                        Set.of(FIRE_LANCE),
                        6);

        Result<BuildResolution, BuildErrorCode> result = engine.resolve(build, "STAFF");

        assertTrue(result.isSuccess());
        BuildResolution resolution =
                ((Result.Success<BuildResolution, BuildErrorCode>) result).value();
        assertEquals(4, resolution.attunementLoad());
        assertEquals(
                DefinitionId.of("move.training_staff.primary_1"),
                resolution.resolvedMoves().get(MovesetBranch.PRIMARY_1));
        assertEquals(12, resolution.scaleStaminaCost(10));
        assertEquals(16, resolution.scaleManaCost(18));
    }

    @Test
    void rejectsFamilyMismatchAndCapacityOverflowBeforeCommit() {
        BuildEngine engine = engine();
        CharacterBuild wrongFamily =
                CharacterBuild.initial()
                        .withTechnique(MovesetBranch.PRIMARY_1, Optional.of(STAFF_TECHNIQUE));
        Result<BuildResolution, BuildErrorCode> family = engine.resolve(wrongFamily, "BOW");
        assertFalse(family.isSuccess());
        assertEquals(BuildErrorCode.BUILD_FAMILY_INCOMPATIBLE, failure(family));

        CharacterBuild overflow =
                new CharacterBuild(Map.of(), Optional.of(EMBER_FORM), Set.of(FIRE_LANCE), 3);
        Result<BuildResolution, BuildErrorCode> capacity = engine.resolve(overflow, "STAFF");
        assertFalse(capacity.isSuccess());
        assertEquals(BuildErrorCode.BUILD_ATTUNEMENT_CAPACITY_EXCEEDED, failure(capacity));
    }

    @Test
    void persistedJsonRoundTripsDeterministically() {
        CharacterBuild build =
                new CharacterBuild(
                        Map.of(MovesetBranch.PRIMARY_1, STAFF_TECHNIQUE),
                        Optional.of(EMBER_FORM),
                        Set.of(FIRE_LANCE),
                        6);

        String encoded = CharacterBuildJsonCodec.encode(build);

        assertEquals(build, CharacterBuildJsonCodec.decode(encoded));
        assertEquals(
                encoded, CharacterBuildJsonCodec.encode(CharacterBuildJsonCodec.decode(encoded)));
    }

    private static BuildEngine engine() {
        Path fixture = Path.of("..", "example-content", "milestone-1").toAbsolutePath().normalize();
        Result<ContentSnapshot, ContentLoadFailure> loaded =
                new ContentSnapshotLoader().load(fixture);
        assertTrue(
                loaded.isSuccess(),
                () ->
                        loaded
                                        instanceof
                                        Result.Failure<ContentSnapshot, ContentLoadFailure> failure
                                ? failure.detail()
                                : "");
        Result<BuildEngine, BuildErrorCode> compiled =
                BuildEngine.compile(
                        ((Result.Success<ContentSnapshot, ContentLoadFailure>) loaded).value());
        assertTrue(compiled.isSuccess());
        BuildEngine engine = ((Result.Success<BuildEngine, BuildErrorCode>) compiled).value();
        assertEquals(5, engine.techniques().size());
        assertEquals(4, engine.forms().size());
        assertEquals(1, engine.attunableEffects().size());
        return engine;
    }

    private static BuildErrorCode failure(Result<BuildResolution, BuildErrorCode> result) {
        return ((Result.Failure<BuildResolution, BuildErrorCode>) result).error();
    }
}
