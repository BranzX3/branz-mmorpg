package com.branz.mmorpg.worldloop.reward;

/** Auditable category totals; eligibility is never a last-hit or damage-only decision. */
public record RewardContribution(
        long damageAndPosture,
        long guardAndControl,
        long healingAndSupport,
        long objectiveActions) {
    public RewardContribution {
        if (damageAndPosture < 0
                || guardAndControl < 0
                || healingAndSupport < 0
                || objectiveActions < 0) {
            throw new IllegalArgumentException("reward contributions must not be negative");
        }
    }

    public RewardContribution plus(RewardContribution other) {
        return new RewardContribution(
                Math.addExact(damageAndPosture, other.damageAndPosture),
                Math.addExact(guardAndControl, other.guardAndControl),
                Math.addExact(healingAndSupport, other.healingAndSupport),
                Math.addExact(objectiveActions, other.objectiveActions));
    }

    public boolean empty() {
        return damageAndPosture == 0
                && guardAndControl == 0
                && healingAndSupport == 0
                && objectiveActions == 0;
    }
}
