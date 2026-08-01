package com.branz.mmorpg.lifeskills.progression;

/** Visible mastery contributions; each source is server-authored and individually bounded. */
public record LifeskillMasteryInputs(
        int rankContribution,
        int toolContribution,
        int workwearContribution,
        int accessoryContribution,
        int regionalKnowledgeContribution,
        int mealOrTonicContribution) {
    public LifeskillMasteryInputs {
        requireContribution(rankContribution, "rankContribution");
        requireContribution(toolContribution, "toolContribution");
        requireContribution(workwearContribution, "workwearContribution");
        requireContribution(accessoryContribution, "accessoryContribution");
        requireContribution(regionalKnowledgeContribution, "regionalKnowledgeContribution");
        requireContribution(mealOrTonicContribution, "mealOrTonicContribution");
    }

    private static void requireContribution(int contribution, String name) {
        if (contribution < 0 || contribution > LifeskillMasteryEngine.MASTERY_MAXIMUM) {
            throw new IllegalArgumentException(name + " must be in [0, 1000]");
        }
    }
}
