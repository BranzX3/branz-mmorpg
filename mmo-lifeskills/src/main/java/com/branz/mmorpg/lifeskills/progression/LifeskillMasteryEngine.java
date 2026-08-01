package com.branz.mmorpg.lifeskills.progression;

import java.util.Objects;

/** Resolves visible mastery and its bounded diminishing-return V1 bonuses. */
public final class LifeskillMasteryEngine {
    public static final int MASTERY_MAXIMUM = 1000;
    public static final double WORK_SPEED_BONUS_CAP = 0.35;
    public static final double BASIC_YIELD_BONUS_CAP = 0.60;
    public static final double RARE_YIELD_RELATIVE_BONUS_CAP = 0.30;

    public LifeskillMasteryProfile resolve(LifeskillMasteryInputs inputs) {
        Objects.requireNonNull(inputs, "inputs");
        long total =
                (long) inputs.rankContribution()
                        + inputs.toolContribution()
                        + inputs.workwearContribution()
                        + inputs.accessoryContribution()
                        + inputs.regionalKnowledgeContribution()
                        + inputs.mealOrTonicContribution();
        int mastery = (int) Math.min(MASTERY_MAXIMUM, total);
        double normalized = mastery / (double) MASTERY_MAXIMUM;
        double diminishing = 1.0 - Math.pow(1.0 - normalized, 2);
        return new LifeskillMasteryProfile(
                mastery,
                WORK_SPEED_BONUS_CAP * diminishing,
                BASIC_YIELD_BONUS_CAP * diminishing,
                RARE_YIELD_RELATIVE_BONUS_CAP * diminishing);
    }
}
