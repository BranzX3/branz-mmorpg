package com.branz.mmorpg.combat.projectile;

import com.branz.mmorpg.combat.hitbox.CombatVector;
import com.branz.mmorpg.combat.hitbox.TargetCollider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Deterministic swept-sphere projectile physics with block-first tie handling. */
public final class ProjectileEngine {
    private static final double EPSILON = 1.0e-9;

    public ProjectileTickResolution advance(ProjectileTickQuery query) {
        Objects.requireNonNull(query, "query");
        ProjectileRuntime current = query.runtime();
        if (current.status().terminal()) {
            throw new IllegalStateException("terminal projectile cannot advance");
        }
        CombatVector start = current.position();
        CombatVector completeEnd = start.add(current.velocity());
        double blockFraction = query.blockContactFraction().orElse(Double.POSITIVE_INFINITY);
        List<Contact> contacts = contacts(current, start, completeEnd, query.candidates());
        ArrayList<ProjectileHit> hits = new ArrayList<>();
        HashSet<java.util.UUID> hitTargets = new HashSet<>(current.hitTargets());
        int remainingPierces = current.remainingPierces();
        for (Contact contact : contacts) {
            if (hitTargets.contains(contact.target().entityId())) {
                continue;
            }
            if (contact.fraction() + EPSILON >= blockFraction) {
                break;
            }
            CombatVector point = interpolate(start, completeEnd, contact.fraction());
            hits.add(
                    new ProjectileHit(
                            contact.target().entityId(),
                            contact.fraction(),
                            point,
                            contact.target().weakPoint()));
            hitTargets.add(contact.target().entityId());
            if (remainingPierces == 0) {
                ProjectileRuntime impacted =
                        nextRuntime(
                                current,
                                point,
                                current.velocity(),
                                remainingPierces,
                                hitTargets,
                                ProjectileStatus.IMPACTED);
                return new ProjectileTickResolution(impacted, start, point, hits);
            }
            remainingPierces--;
        }
        if (blockFraction <= 1) {
            CombatVector blockedAt = interpolate(start, completeEnd, blockFraction);
            ProjectileRuntime blocked =
                    nextRuntime(
                            current,
                            blockedAt,
                            current.velocity(),
                            remainingPierces,
                            hitTargets,
                            ProjectileStatus.BLOCKED);
            return new ProjectileTickResolution(blocked, start, blockedAt, hits);
        }
        int nextAge = current.ageTicks() + 1;
        ProjectileStatus status =
                nextAge >= current.profile().lifetimeTicks()
                        ? ProjectileStatus.EXPIRED
                        : ProjectileStatus.FLYING;
        CombatVector nextVelocity =
                new CombatVector(
                                current.velocity().x(),
                                current.velocity().y() - current.profile().gravityPerTick(),
                                current.velocity().z())
                        .multiply(current.profile().dragPerTick());
        ProjectileRuntime advanced =
                nextRuntime(
                        current, completeEnd, nextVelocity, remainingPierces, hitTargets, status);
        return new ProjectileTickResolution(advanced, start, completeEnd, hits);
    }

    private static ProjectileRuntime nextRuntime(
            ProjectileRuntime current,
            CombatVector position,
            CombatVector velocity,
            int remainingPierces,
            java.util.Set<java.util.UUID> hitTargets,
            ProjectileStatus status) {
        return new ProjectileRuntime(
                current.identity(),
                current.profile(),
                position,
                velocity,
                current.ageTicks() + 1,
                remainingPierces,
                hitTargets,
                status);
    }

    private static List<Contact> contacts(
            ProjectileRuntime runtime,
            CombatVector start,
            CombatVector end,
            List<TargetCollider> candidates) {
        ArrayList<Contact> contacts = new ArrayList<>();
        for (TargetCollider target : candidates) {
            Objects.requireNonNull(target, "target");
            if (!target.eligible()
                    || runtime.hitTargets().contains(target.entityId())
                    || runtime.identity().ownerEntityId().equals(target.entityId())) {
                continue;
            }
            double fraction =
                    contactFraction(start, end, runtime.profile().collisionRadius(), target);
            if (Double.isFinite(fraction)) {
                contacts.add(new Contact(target, fraction));
            }
        }
        contacts.sort(
                Comparator.comparingDouble(Contact::fraction)
                        .thenComparing(contact -> contact.target().entityId()));
        return List.copyOf(contacts);
    }

    private static double contactFraction(
            CombatVector start, CombatVector end, double projectileRadius, TargetCollider target) {
        double horizontalRadius = projectileRadius + target.radius();
        double[] minimum = {
            target.feetPosition().x() - horizontalRadius,
            target.feetPosition().y() - projectileRadius,
            target.feetPosition().z() - horizontalRadius
        };
        double[] maximum = {
            target.feetPosition().x() + horizontalRadius,
            target.feetPosition().y() + target.height() + projectileRadius,
            target.feetPosition().z() + horizontalRadius
        };
        double[] origin = {start.x(), start.y(), start.z()};
        CombatVector delta = end.subtract(start);
        double[] direction = {delta.x(), delta.y(), delta.z()};
        double enter = 0;
        double exit = 1;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(direction[axis]) < EPSILON) {
                if (origin[axis] < minimum[axis] || origin[axis] > maximum[axis]) {
                    return Double.NaN;
                }
                continue;
            }
            double first = (minimum[axis] - origin[axis]) / direction[axis];
            double second = (maximum[axis] - origin[axis]) / direction[axis];
            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }
            enter = Math.max(enter, first);
            exit = Math.min(exit, second);
            if (enter - EPSILON > exit) {
                return Double.NaN;
            }
        }
        return enter >= 0 && enter <= 1 ? enter : Double.NaN;
    }

    private static CombatVector interpolate(CombatVector start, CombatVector end, double fraction) {
        return start.add(end.subtract(start).multiply(fraction));
    }

    private record Contact(TargetCollider target, double fraction) {}
}
