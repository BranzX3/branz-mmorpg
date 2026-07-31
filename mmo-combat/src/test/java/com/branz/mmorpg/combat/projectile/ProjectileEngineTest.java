package com.branz.mmorpg.combat.projectile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.combat.hitbox.CombatVector;
import com.branz.mmorpg.combat.hitbox.TargetCollider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectileEngineTest {
    private static final UUID OWNER = new UUID(0, 1);
    private static final ProjectileProfile PROFILE =
            new ProjectileProfile(4, 0.05, 0.99, 0.1, 3, 0);
    private final ProjectileEngine engine = new ProjectileEngine();

    @Test
    void sweptContactPreventsTunnellingAndStopsAtFirstImpact() {
        UUID targetId = new UUID(0, 2);
        ProjectileRuntime runtime = launch(PROFILE);

        ProjectileTickResolution result =
                engine.advance(
                        new ProjectileTickQuery(
                                runtime, List.of(target(targetId, 2, 0)), OptionalDouble.empty()));

        assertEquals(ProjectileStatus.IMPACTED, result.runtime().status());
        assertEquals(
                List.of(targetId), result.hits().stream().map(ProjectileHit::entityId).toList());
        assertTrue(result.pathEnd().x() < 2);
    }

    @Test
    void blockWinsAnExactContactTieAndNoEntityHitLeaksThrough() {
        ProjectileRuntime runtime = launch(PROFILE);
        TargetCollider target = target(new UUID(0, 3), 2, 0);
        double entityContact =
                engine.advance(
                                new ProjectileTickQuery(
                                        runtime, List.of(target), OptionalDouble.empty()))
                        .hits()
                        .getFirst()
                        .contactFraction();

        ProjectileTickResolution blocked =
                engine.advance(
                        new ProjectileTickQuery(
                                runtime, List.of(target), OptionalDouble.of(entityContact)));

        assertEquals(ProjectileStatus.BLOCKED, blocked.runtime().status());
        assertTrue(blocked.hits().isEmpty());
    }

    @Test
    void pierceContactsAreGloballyOrderedAndNeverRepeatAcrossTicks() {
        ProjectileProfile piercing = new ProjectileProfile(4, 0, 1, 0.1, 5, 2);
        List<TargetCollider> candidates =
                List.of(
                        target(new UUID(0, 30), 3, 0),
                        target(new UUID(0, 10), 1, 0),
                        target(new UUID(0, 20), 2, 0));
        List<UUID> expected = List.of(new UUID(0, 10), new UUID(0, 20), new UUID(0, 30));

        for (int seed = 0; seed < 1_000; seed++) {
            ArrayList<TargetCollider> shuffled = new ArrayList<>(candidates);
            Collections.shuffle(shuffled, new Random(seed));
            ProjectileTickResolution result =
                    engine.advance(
                            new ProjectileTickQuery(
                                    launch(piercing), shuffled, OptionalDouble.empty()));
            assertEquals(expected, result.hits().stream().map(ProjectileHit::entityId).toList());
            assertEquals(ProjectileStatus.IMPACTED, result.runtime().status());
            assertEquals(3, result.runtime().hitTargets().size());
        }
    }

    @Test
    void gravityDragAndLifetimeUseExactServerTickBoundaries() {
        ProjectileRuntime first =
                engine.advance(
                                new ProjectileTickQuery(
                                        launch(PROFILE), List.of(), OptionalDouble.empty()))
                        .runtime();
        ProjectileRuntime second =
                engine.advance(new ProjectileTickQuery(first, List.of(), OptionalDouble.empty()))
                        .runtime();
        ProjectileRuntime third =
                engine.advance(new ProjectileTickQuery(second, List.of(), OptionalDouble.empty()))
                        .runtime();

        assertEquals(3.96, first.velocity().x(), 1.0e-12);
        assertEquals(-0.0495, first.velocity().y(), 1.0e-12);
        assertEquals(ProjectileStatus.FLYING, second.status());
        assertEquals(ProjectileStatus.EXPIRED, third.status());
        assertThrows(
                IllegalStateException.class,
                () ->
                        engine.advance(
                                new ProjectileTickQuery(third, List.of(), OptionalDouble.empty())));
    }

    private static ProjectileRuntime launch(ProjectileProfile profile) {
        return ProjectileRuntime.launch(
                new ProjectileIdentity(
                        new UUID(0, 99),
                        OWNER,
                        DefinitionId.of("move.training_bow.quick_shot"),
                        "test-v1",
                        Optional.of(DefinitionId.of("ammo.training_arrow")),
                        "BOW_PRIMARY"),
                profile,
                new CombatVector(0, 1, 0),
                new CombatVector(1, 0, 0),
                1);
    }

    private static TargetCollider target(UUID id, double x, double z) {
        return new TargetCollider(id, new CombatVector(x, 0, z), 0.3, 2, true, true, false);
    }
}
