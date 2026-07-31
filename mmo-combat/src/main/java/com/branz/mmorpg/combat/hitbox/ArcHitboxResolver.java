package com.branz.mmorpg.combat.hitbox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Pure server-side ARC contact and deterministic limited-target ordering. */
public final class ArcHitboxResolver {
    private static final Comparator<ResolvedTarget> ORDER =
            Comparator.comparing(ResolvedTarget::weakPoint)
                    .reversed()
                    .thenComparingDouble(ResolvedTarget::distance)
                    .thenComparingDouble(ResolvedTarget::angleDegrees)
                    .thenComparing(ResolvedTarget::entityId);

    public List<ResolvedTarget> resolve(ArcHitboxQuery query, List<TargetCollider> candidates) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(candidates, "candidates");
        ArrayList<ResolvedTarget> contacts = new ArrayList<>();
        for (TargetCollider target : candidates) {
            Objects.requireNonNull(target, "target");
            if (!target.eligible() || !target.lineOfSight() || !verticalOverlap(query, target)) {
                continue;
            }
            CombatVector offset = target.feetPosition().subtract(query.origin());
            double distance = offset.horizontalLength();
            if (distance > query.range() + target.radius()) {
                continue;
            }
            double angle = angle(query.forward(), offset, distance);
            if (angle > query.angleDegrees() / 2.0) {
                continue;
            }
            contacts.add(
                    new ResolvedTarget(target.entityId(), target.weakPoint(), distance, angle));
        }
        contacts.sort(ORDER);
        return List.copyOf(contacts.subList(0, Math.min(query.maximumTargets(), contacts.size())));
    }

    private static boolean verticalOverlap(ArcHitboxQuery query, TargetCollider target) {
        double hitboxMinimum = query.origin().y() + query.verticalMinimum();
        double hitboxMaximum = query.origin().y() + query.verticalMaximum();
        double targetMinimum = target.feetPosition().y();
        double targetMaximum = targetMinimum + target.height();
        return targetMaximum >= hitboxMinimum && targetMinimum <= hitboxMaximum;
    }

    private static double angle(CombatVector forward, CombatVector offset, double distance) {
        if (distance == 0) {
            return 0;
        }
        double dot = (forward.x() * offset.x() + forward.z() * offset.z()) / distance;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
    }
}
