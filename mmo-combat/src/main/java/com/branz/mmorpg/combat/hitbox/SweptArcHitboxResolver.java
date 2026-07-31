package com.branz.mmorpg.combat.hitbox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic sub-tick sweep between two server-owned ARC transforms. */
public final class SweptArcHitboxResolver {
    public static final double MAXIMUM_LINEAR_STEP = 0.125;
    public static final double MAXIMUM_ANGULAR_STEP_DEGREES = 2.0;
    public static final int MAXIMUM_SEGMENTS = 128;

    private static final Comparator<ResolvedTarget> ORDER =
            Comparator.comparing(ResolvedTarget::weakPoint)
                    .reversed()
                    .thenComparingDouble(ResolvedTarget::distance)
                    .thenComparingDouble(ResolvedTarget::angleDegrees)
                    .thenComparing(ResolvedTarget::entityId);

    private final ArcHitboxResolver arcs = new ArcHitboxResolver();

    public SweptArcResolution resolve(SweptArcHitboxQuery sweep, List<TargetCollider> candidates) {
        Objects.requireNonNull(sweep, "sweep");
        Objects.requireNonNull(candidates, "candidates");
        SamplePlan plan = samplePlan(sweep);
        Map<java.util.UUID, ResolvedTarget> bestByTarget = new HashMap<>();
        ArrayList<CombatVector> origins = new ArrayList<>();
        for (int index = 0; index <= plan.segments(); index++) {
            double ratio = (double) index / plan.segments();
            ArcHitboxQuery sample = interpolate(sweep, ratio);
            origins.add(sample.origin());
            for (ResolvedTarget target : arcs.resolve(sample, candidates)) {
                bestByTarget.merge(
                        target.entityId(),
                        target,
                        (existing, replacement) ->
                                ORDER.compare(existing, replacement) <= 0 ? existing : replacement);
            }
        }
        List<ResolvedTarget> ordered = bestByTarget.values().stream().sorted(ORDER).toList();
        int maximum = sweep.current().maximumTargets();
        return new SweptArcResolution(
                ordered.subList(0, Math.min(maximum, ordered.size())), origins, plan.capped());
    }

    private static SamplePlan samplePlan(SweptArcHitboxQuery sweep) {
        CombatVector delta = sweep.current().origin().subtract(sweep.previous().origin());
        double linearDistance =
                Math.sqrt(delta.x() * delta.x() + delta.y() * delta.y() + delta.z() * delta.z());
        double angularDistance =
                Math.abs(
                        shortestAngle(
                                yaw(sweep.current().forward()) - yaw(sweep.previous().forward())));
        int requested =
                Math.max(
                        1,
                        Math.max(
                                (int) Math.ceil(linearDistance / MAXIMUM_LINEAR_STEP),
                                (int)
                                        Math.ceil(
                                                Math.toDegrees(angularDistance)
                                                        / MAXIMUM_ANGULAR_STEP_DEGREES)));
        return new SamplePlan(Math.min(requested, MAXIMUM_SEGMENTS), requested > MAXIMUM_SEGMENTS);
    }

    private static ArcHitboxQuery interpolate(SweptArcHitboxQuery sweep, double ratio) {
        ArcHitboxQuery previous = sweep.previous();
        ArcHitboxQuery current = sweep.current();
        CombatVector origin =
                new CombatVector(
                        lerp(previous.origin().x(), current.origin().x(), ratio),
                        lerp(previous.origin().y(), current.origin().y(), ratio),
                        lerp(previous.origin().z(), current.origin().z(), ratio));
        double previousYaw = yaw(previous.forward());
        double interpolatedYaw =
                previousYaw + shortestAngle(yaw(current.forward()) - previousYaw) * ratio;
        CombatVector forward =
                new CombatVector(Math.sin(interpolatedYaw), 0, Math.cos(interpolatedYaw));
        return new ArcHitboxQuery(
                origin,
                forward,
                current.range(),
                current.angleDegrees(),
                current.verticalMinimum(),
                current.verticalMaximum(),
                8);
    }

    private static double yaw(CombatVector direction) {
        return Math.atan2(direction.x(), direction.z());
    }

    private static double shortestAngle(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }

    private static double lerp(double from, double to, double ratio) {
        return from + (to - from) * ratio;
    }

    private record SamplePlan(int segments, boolean capped) {}
}
