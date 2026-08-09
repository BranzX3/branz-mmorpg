package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.combat.health.CombatHealthEngine;
import com.branz.mmorpg.combat.health.CombatHealthProfile;
import com.branz.mmorpg.combat.health.CombatHealthRuntime;
import com.branz.mmorpg.combat.hitbox.ArcHitboxQuery;
import com.branz.mmorpg.combat.hitbox.CombatVector;
import com.branz.mmorpg.combat.hitbox.SweptArcHitboxQuery;
import com.branz.mmorpg.combat.hitbox.SweptArcHitboxResolver;
import com.branz.mmorpg.combat.hitbox.SweptArcResolution;
import com.branz.mmorpg.combat.hitbox.TargetCollider;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeleeCombatAcceptanceTest {
    private final CombatHealthEngine health =
            new CombatHealthEngine(CombatHealthProfile.trainingEnemy());

    @Test
    void sweptArcHitFlowsIntoAuthoritativeLethalDamageAndFeedback() {
        UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        ArcHitboxQuery arc =
                new ArcHitboxQuery(
                        new CombatVector(0.0, 1.0, 0.0),
                        new CombatVector(0.0, 0.0, 1.0),
                        3.0,
                        90.0,
                        -1.25,
                        1.25,
                        1);
        TargetCollider zombieLikeTarget =
                new TargetCollider(
                        targetId, new CombatVector(0.0, 0.0, 1.5), 0.3, 1.8, true, true, false);

        SweptArcResolution hitbox =
                new SweptArcHitboxResolver()
                        .resolve(new SweptArcHitboxQuery(arc, arc), List.of(zombieLikeTarget));

        assertEquals(1, hitbox.targets().size());
        assertEquals(targetId, hitbox.targets().getFirst().entityId());
        assertFalse(hitbox.samplingCapped());

        CombatHealthRuntime full = CombatHealthRuntime.full(health.profile(), 20);
        MeleeTargetDamageCoordinator.MeleeTargetDamageResult damage =
                MeleeTargetDamageCoordinator.apply(
                        health, full, 21, health.profile().maximum() * 2.0);

        assertEquals(health.profile().maximum(), damage.appliedDamage());
        assertEquals(0.0, damage.runtime().current());
        assertTrue(damage.lethalNow());
        assertTrue(damage.feedback().isPresent());
    }

    @Test
    void outOfArcTargetNeverReachesDamageApplicationBoundary() {
        UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        ArcHitboxQuery arc =
                new ArcHitboxQuery(
                        new CombatVector(0.0, 1.0, 0.0),
                        new CombatVector(0.0, 0.0, 1.0),
                        3.0,
                        70.0,
                        -1.25,
                        1.25,
                        1);
        TargetCollider behindAttacker =
                new TargetCollider(
                        targetId, new CombatVector(0.0, 0.0, -1.5), 0.3, 1.8, true, true, false);

        SweptArcResolution hitbox =
                new SweptArcHitboxResolver()
                        .resolve(new SweptArcHitboxQuery(arc, arc), List.of(behindAttacker));

        assertTrue(hitbox.targets().isEmpty());
    }
}
