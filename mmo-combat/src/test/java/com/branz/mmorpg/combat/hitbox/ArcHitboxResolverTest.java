package com.branz.mmorpg.combat.hitbox;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArcHitboxResolverTest {
    private static final ArcHitboxQuery QUERY =
            new ArcHitboxQuery(
                    new CombatVector(0, 0, 0), new CombatVector(0, 0, 1), 3, 90, -0.5, 2.5, 3);

    private final ArcHitboxResolver resolver = new ArcHitboxResolver();

    @Test
    void rejectsRangeAngleVerticalEligibilityAndLineOfSightFailures() {
        TargetCollider valid = target(1, 0, 1, true, true, false);
        List<TargetCollider> candidates =
                List.of(
                        valid,
                        target(2, 0, 4, true, true, false),
                        target(3, 3, 0, true, true, false),
                        new TargetCollider(
                                id(4), new CombatVector(0, 4, 1), 0.3, 1.8, true, true, false),
                        target(5, 0, 1, false, true, false),
                        target(6, 0, 1, true, false, false));

        assertEquals(
                List.of(id(1)),
                resolver.resolve(QUERY, candidates).stream()
                        .map(ResolvedTarget::entityId)
                        .toList());
    }

    @Test
    void targetLimitOrderingIsWeakPointThenDistanceThenAngleThenUuid() {
        TargetCollider nearCenter = target(20, 0, 1, true, true, false);
        TargetCollider widerAngle = target(30, 1, 1, true, true, false);
        TargetCollider weakFar = target(40, 0, 2.5, true, true, true);
        TargetCollider sameMetricsLowerUuid = target(10, 0, 1, true, true, false);

        List<ResolvedTarget> resolved =
                resolver.resolve(
                        QUERY, List.of(nearCenter, widerAngle, weakFar, sameMetricsLowerUuid));

        assertEquals(
                List.of(id(40), id(10), id(20)),
                resolved.stream().map(ResolvedTarget::entityId).toList());
    }

    @Test
    void candidateCollectionOrderCannotChangeAuthoritativeResult() {
        ArrayList<TargetCollider> candidates =
                new ArrayList<>(
                        List.of(
                                target(1, -0.4, 1.2, true, true, false),
                                target(2, 0.4, 1.2, true, true, false),
                                target(3, 0, 2, true, true, true),
                                target(4, 0, 2.5, true, true, false)));
        List<ResolvedTarget> expected = resolver.resolve(QUERY, candidates);

        for (int seed = 0; seed < 100; seed++) {
            Collections.shuffle(candidates, new java.util.Random(seed));
            assertEquals(expected, resolver.resolve(QUERY, candidates));
        }
    }

    private static TargetCollider target(
            long id, double x, double z, boolean eligible, boolean lineOfSight, boolean weakPoint) {
        return new TargetCollider(
                id(id), new CombatVector(x, 0, z), 0.3, 1.8, eligible, lineOfSight, weakPoint);
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }
}
