package com.branz.mmorpg.api.combat;

import java.util.Objects;

/**
 * What a damage attempt actually did.
 *
 * <p>Every stage of the pipeline is reported, not just the final number, so a
 * player can be shown why a hit was small and an operator can tell a mitigation
 * bug from a coefficient bug without reproducing it.
 *
 * @param rejection  why nothing happened, or null when the attempt landed
 * @param raw        power after offensive modifiers, before critical
 * @param critical   whether it was a critical hit
 * @param afterCrit  power after the critical multiplier
 * @param mitigated  amount removed by defense or resistance
 * @param absorbed   amount removed by shields
 * @param applied    health actually removed
 * @param lethal     whether this brought the target to zero
 */
public record DamageResult(
        RejectionReason rejection,
        double raw,
        boolean critical,
        double afterCrit,
        double mitigated,
        double absorbed,
        double applied,
        boolean lethal) {

    public DamageResult {
        if (rejection == null && (!valid(raw) || !valid(afterCrit) || !valid(mitigated)
                || !valid(absorbed) || !valid(applied))) {
            throw new IllegalArgumentException("damage stages must be finite and non-negative");
        }
    }

    public static DamageResult rejected(RejectionReason reason) {
        return new DamageResult(Objects.requireNonNull(reason, "reason"),
                0.0, false, 0.0, 0.0, 0.0, 0.0, false);
    }

    public boolean landed() {
        return rejection == null;
    }

    private static boolean valid(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }
}
