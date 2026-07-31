package com.branz.mmorpg.combat.hitbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure viewer-debug outline geometry for an authoritative horizontal ARC query. */
public final class ArcDebugGeometry {
    public List<CombatVector> outline(ArcHitboxQuery query, int arcSegments, int radialSegments) {
        Objects.requireNonNull(query, "query");
        if (arcSegments < 2 || arcSegments > 64 || radialSegments < 1 || radialSegments > 16) {
            throw new IllegalArgumentException("invalid ARC debug segment count");
        }
        ArrayList<CombatVector> points = new ArrayList<>();
        double halfAngle = query.angleDegrees() / 2.0;
        for (int index = 0; index <= arcSegments; index++) {
            double angle = -halfAngle + query.angleDegrees() * index / arcSegments;
            points.add(point(query, angle, query.range()));
        }
        for (double angle : new double[] {-halfAngle, 0, halfAngle}) {
            for (int index = 1; index <= radialSegments; index++) {
                points.add(point(query, angle, query.range() * index / radialSegments));
            }
        }
        return List.copyOf(points);
    }

    private static CombatVector point(ArcHitboxQuery query, double angleDegrees, double distance) {
        double radians = Math.toRadians(angleDegrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        CombatVector forward = query.forward();
        double x = forward.x() * cosine - forward.z() * sine;
        double z = forward.x() * sine + forward.z() * cosine;
        return new CombatVector(
                query.origin().x() + x * distance,
                query.origin().y(),
                query.origin().z() + z * distance);
    }
}
