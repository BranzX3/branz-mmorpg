package com.branz.mmorpg.combat.hitbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SweptArcHitboxResolverTest {
    private final ArcHitboxResolver arcs = new ArcHitboxResolver();
    private final SweptArcHitboxResolver swept = new SweptArcHitboxResolver();

    @Test
    void linearSweepHitsTargetMissedByBothEndpointArcs() {
        ArcHitboxQuery previous = arc(new CombatVector(-1, 0, 0), new CombatVector(0, 0, 1), 4);
        ArcHitboxQuery current = arc(new CombatVector(1, 0, 0), new CombatVector(0, 0, 1), 4);
        TargetCollider between = target("00000000-0000-0000-0000-000000000001", 0, 0.8);

        assertTrue(arcs.resolve(previous, List.of(between)).isEmpty());
        assertTrue(arcs.resolve(current, List.of(between)).isEmpty());
        SweptArcResolution resolution =
                swept.resolve(new SweptArcHitboxQuery(previous, current), List.of(between));

        assertEquals(List.of(between.entityId()), ids(resolution));
        assertEquals(17, resolution.sampledOrigins().size());
        assertFalse(resolution.samplingCapped());
    }

    @Test
    void rotationSweepUsesShortestYawAndFindsIntermediateContact() {
        ArcHitboxQuery previous = arc(new CombatVector(0, 0, 0), new CombatVector(0, 0, 1), 4);
        ArcHitboxQuery current = arc(new CombatVector(0, 0, 0), new CombatVector(1, 0, 0), 4);
        TargetCollider diagonal = target("00000000-0000-0000-0000-000000000002", 0.6, 0.6);

        assertTrue(arcs.resolve(previous, List.of(diagonal)).isEmpty());
        assertTrue(arcs.resolve(current, List.of(diagonal)).isEmpty());
        SweptArcResolution resolution =
                swept.resolve(new SweptArcHitboxQuery(previous, current), List.of(diagonal));

        assertEquals(List.of(diagonal.entityId()), ids(resolution));
        assertEquals(46, resolution.sampledOrigins().size());
    }

    @Test
    void targetIsDeduplicatedAndGlobalOrderingIsCollectionIndependent() {
        ArcHitboxQuery previous = arc(new CombatVector(-0.5, 0, 0), new CombatVector(0, 0, 1), 4);
        ArcHitboxQuery current = arc(new CombatVector(0.5, 0, 0), new CombatVector(0, 0, 1), 4);
        List<TargetCollider> candidates =
                List.of(
                        target("00000000-0000-0000-0000-000000000003", 0, 0.4),
                        target("00000000-0000-0000-0000-000000000004", 0, 0.7),
                        target("00000000-0000-0000-0000-000000000005", 0, 1.0));
        List<UUID> expected =
                ids(swept.resolve(new SweptArcHitboxQuery(previous, current), candidates));

        assertEquals(3, expected.size());
        for (int seed = 0; seed < 1_000; seed++) {
            ArrayList<TargetCollider> shuffled = new ArrayList<>(candidates);
            Collections.shuffle(shuffled, new java.util.Random(seed));
            assertEquals(
                    expected,
                    ids(swept.resolve(new SweptArcHitboxQuery(previous, current), shuffled)));
        }
    }

    @Test
    void extremeMotionIsBoundedAndStillIncludesBothEndpoints() {
        ArcHitboxQuery previous = arc(new CombatVector(0, 0, 0), new CombatVector(0, 0, 1), 4);
        ArcHitboxQuery current = arc(new CombatVector(100, 0, 0), new CombatVector(0, 0, -1), 4);

        SweptArcResolution resolution =
                swept.resolve(new SweptArcHitboxQuery(previous, current), List.of());

        assertTrue(resolution.samplingCapped());
        assertEquals(
                SweptArcHitboxResolver.MAXIMUM_SEGMENTS + 1, resolution.sampledOrigins().size());
        assertEquals(previous.origin(), resolution.sampledOrigins().getFirst());
        assertEquals(current.origin(), resolution.sampledOrigins().getLast());
    }

    private static ArcHitboxQuery arc(
            CombatVector origin, CombatVector forward, int maximumTargets) {
        return new ArcHitboxQuery(origin, forward, 1, 10, -0.5, 2, maximumTargets);
    }

    private static TargetCollider target(String id, double x, double z) {
        return new TargetCollider(
                UUID.fromString(id), new CombatVector(x, 0, z), 0.1, 1.8, true, true, false);
    }

    private static List<UUID> ids(SweptArcResolution resolution) {
        return resolution.targets().stream().map(ResolvedTarget::entityId).toList();
    }
}
