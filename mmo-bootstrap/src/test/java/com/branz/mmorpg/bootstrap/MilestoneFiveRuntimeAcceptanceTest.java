package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.ErrorCode;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.action.ActionPhase;
import com.branz.mmorpg.combat.action.ActionTimeline;
import com.branz.mmorpg.combat.action.ActionTraceEventType;
import com.branz.mmorpg.combat.action.CombatResources;
import com.branz.mmorpg.combat.move.MoveDefinition;
import com.branz.mmorpg.combat.move.MoveEngine;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.content.snapshot.ContentSnapshotLoader;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.magic.definition.SpellCastType;
import com.branz.mmorpg.magic.definition.SpellDeliveryType;
import com.branz.mmorpg.magic.definition.SpellEngine;
import com.branz.mmorpg.progression.build.BuildEngine;
import com.branz.mmorpg.progression.build.BuildResolution;
import com.branz.mmorpg.progression.build.CharacterBuild;
import com.branz.mmorpg.progression.build.CharacterBuildJsonCodec;
import com.branz.mmorpg.progression.build.MovesetBranch;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Integrated content/runtime gate for the complete Milestone 5 family matrix. */
class MilestoneFiveRuntimeAcceptanceTest {
    @Test
    void everyWeaponFamilyResolvesItsPersistableTechniqueAndCompletesAnAction() {
        ContentSnapshot snapshot = snapshot();
        ItemEngine items = success(ItemEngine.compile(snapshot));
        MoveEngine moves = success(MoveEngine.compile(snapshot));
        BuildEngine builds = success(BuildEngine.compile(snapshot));
        List<FamilyFixture> families =
                List.of(
                        new FamilyFixture(
                                "GREATSWORD",
                                "weapon.training_greatsword",
                                "technique.greatsword.cleave",
                                MovesetBranch.PRIMARY_1),
                        new FamilyFixture(
                                "SWORD_SHIELD",
                                "weapon.training_sword",
                                "technique.sword_shield.guard_strike",
                                MovesetBranch.PRIMARY_1),
                        new FamilyFixture(
                                "BOW",
                                "weapon.training_bow",
                                "technique.bow.quick_shot",
                                MovesetBranch.SECONDARY),
                        new FamilyFixture(
                                "CROSSBOW",
                                "weapon.training_crossbow",
                                "technique.crossbow.ready_shot",
                                MovesetBranch.SECONDARY),
                        new FamilyFixture(
                                "STAFF",
                                "weapon.training_staff",
                                "technique.staff.sweep",
                                MovesetBranch.PRIMARY_1));

        for (FamilyFixture family : families) {
            assertEquals(
                    family.family(),
                    items.find(family.itemId())
                            .orElseThrow()
                            .weaponProfile()
                            .orElseThrow()
                            .family());
            CharacterBuild build =
                    CharacterBuild.initial()
                            .withTechnique(family.branch(), Optional.of(family.techniqueId()));
            assertEquals(
                    build, CharacterBuildJsonCodec.decode(CharacterBuildJsonCodec.encode(build)));
            BuildResolution resolved = success(builds.resolve(build, family.family()));
            MoveDefinition move =
                    moves.find(resolved.resolvedMoves().get(family.branch())).orElseThrow();
            assertEquals(family.family(), move.family());

            ActionTimeline timeline =
                    success(ActionTimeline.start(move, CombatResources.full(1000, 100, 100)));
            int safety = 0;
            while (!timeline.phase().terminal() && safety++ < 300) {
                timeline = success(timeline.advance());
            }
            assertEquals(ActionPhase.COMPLETE, timeline.phase());
            assertTrue(
                    timeline.trace().stream()
                            .anyMatch(event -> event.type() == ActionTraceEventType.HITBOX_OPENED));
        }
        assertTrue(
                items.find(DefinitionId.of("equipment.training_shield"))
                        .orElseThrow()
                        .shieldProfile()
                        .isPresent());
    }

    @Test
    void EmberAndRunicSetCompilesEveryAuthoredV1CastAndDelivery() {
        SpellEngine spells = success(SpellEngine.compile(snapshot()));

        assertEquals(5, spells.all().size());
        assertSpell(
                spells, "spell.ember.cinder_snap", SpellCastType.INSTANT, SpellDeliveryType.DIRECT);
        assertSpell(
                spells,
                "spell.ember.fire_lance",
                SpellCastType.CHARGE,
                SpellDeliveryType.PROJECTILE);
        assertSpell(
                spells,
                "spell.ember.scorching_ground",
                SpellCastType.WINDUP,
                SpellDeliveryType.ZONE);
        assertSpell(
                spells, "spell.ember.flame_torrent", SpellCastType.CHANNEL, SpellDeliveryType.BEAM);
        assertSpell(
                spells, "spell.runic.ember_edge", SpellCastType.INSTANT, SpellDeliveryType.IMBUE);
    }

    private static void assertSpell(
            SpellEngine engine, String id, SpellCastType cast, SpellDeliveryType delivery) {
        var spell = engine.find(DefinitionId.of(id)).orElseThrow();
        assertEquals(cast, spell.castType());
        assertEquals(delivery, spell.deliveryType());
    }

    private static ContentSnapshot snapshot() {
        Path fixture = Path.of("..", "example-content", "milestone-1").toAbsolutePath().normalize();
        return success(new ContentSnapshotLoader().load(fixture));
    }

    private static <T, E extends ErrorCode> T success(Result<T, E> result) {
        assertTrue(
                result.isSuccess(),
                () -> result instanceof Result.Failure<T, E> failure ? failure.detail() : "");
        return ((Result.Success<T, E>) result).value();
    }

    private record FamilyFixture(
            String family, DefinitionId itemId, DefinitionId techniqueId, MovesetBranch branch) {
        private FamilyFixture(
                String family, String itemId, String techniqueId, MovesetBranch branch) {
            this(family, DefinitionId.of(itemId), DefinitionId.of(techniqueId), branch);
        }
    }
}
