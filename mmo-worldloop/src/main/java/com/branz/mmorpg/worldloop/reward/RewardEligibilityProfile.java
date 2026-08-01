package com.branz.mmorpg.worldloop.reward;

/** Authored category floors and inactivity threshold for one encounter reward pool. */
public record RewardEligibilityProfile(
        long damageAndPostureFloor,
        long guardAndControlFloor,
        long healingAndSupportFloor,
        long objectiveActionFloor,
        long maximumIdleTicks) {
    public RewardEligibilityProfile {
        if (damageAndPostureFloor < 1
                || guardAndControlFloor < 1
                || healingAndSupportFloor < 1
                || objectiveActionFloor < 1
                || maximumIdleTicks < 1) {
            throw new IllegalArgumentException("reward floors and idle threshold must be positive");
        }
    }

    public boolean meaningful(RewardContribution contribution) {
        return contribution.damageAndPosture() >= damageAndPostureFloor
                || contribution.guardAndControl() >= guardAndControlFloor
                || contribution.healingAndSupport() >= healingAndSupportFloor
                || contribution.objectiveActions() >= objectiveActionFloor;
    }
}
