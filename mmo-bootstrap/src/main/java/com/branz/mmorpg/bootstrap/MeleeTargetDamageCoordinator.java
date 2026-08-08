package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.combat.health.CombatHealthEngine;
import com.branz.mmorpg.combat.health.CombatHealthResolution;
import com.branz.mmorpg.combat.health.CombatHealthRuntime;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies one authoritative melee damage result and derives presentation from the amount actually
 * applied by the MMO health engine.
 */
final class MeleeTargetDamageCoordinator {
    private MeleeTargetDamageCoordinator() {}

    static MeleeTargetDamageResult apply(
            CombatHealthEngine health,
            CombatHealthRuntime current,
            long tick,
            double resolvedDamage) {
        Objects.requireNonNull(health, "health");
        Objects.requireNonNull(current, "current");
        CombatHealthResolution resolution = health.damage(current, tick, resolvedDamage);
        return new MeleeTargetDamageResult(
                resolution.runtime(),
                resolution.appliedAmount(),
                resolution.lethalNow(),
                MeleeHitFeedbackPolicy.forAppliedDamage(resolution.appliedAmount()));
    }

    record MeleeTargetDamageResult(
            CombatHealthRuntime runtime,
            double appliedDamage,
            boolean lethalNow,
            Optional<MeleeHitFeedbackPolicy.MeleeHitFeedbackSpec> feedback) {
        MeleeTargetDamageResult {
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(feedback, "feedback");
            if (!Double.isFinite(appliedDamage) || appliedDamage < 0.0) {
                throw new IllegalArgumentException("appliedDamage must be finite and non-negative");
            }
            if (lethalNow != runtime.dead()) {
                throw new IllegalArgumentException("lethalNow must match the resulting runtime");
            }
            if ((appliedDamage > 0.0) != feedback.isPresent()) {
                throw new IllegalArgumentException(
                        "feedback presence must match positive authoritative applied damage");
            }
        }
    }
}
