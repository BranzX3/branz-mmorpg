package com.branz.mmorpg.bootstrap;

import java.util.Optional;

/** Presentation-only melee hit feedback derived from authoritative MMO damage. */
final class MeleeHitFeedbackPolicy {
    private static final int MAX_PARTICLES = 8;

    private MeleeHitFeedbackPolicy() {}

    static Optional<MeleeHitFeedbackSpec> forAppliedDamage(double appliedDamage) {
        if (!Double.isFinite(appliedDamage) || appliedDamage <= 0.0) {
            return Optional.empty();
        }
        int particles = Math.clamp((int) Math.ceil(appliedDamage / 8.0), 1, MAX_PARTICLES);
        float pitch = (float) Math.clamp(0.9 + (appliedDamage / 120.0), 0.9, 1.2);
        return Optional.of(new MeleeHitFeedbackSpec(particles, 0.75f, pitch));
    }

    record MeleeHitFeedbackSpec(int particleCount, float volume, float pitch) {
        MeleeHitFeedbackSpec {
            if (particleCount < 1 || particleCount > MAX_PARTICLES) {
                throw new IllegalArgumentException("particleCount out of range");
            }
            if (volume <= 0.0f || pitch <= 0.0f) {
                throw new IllegalArgumentException("volume and pitch must be positive");
            }
        }
    }
}
