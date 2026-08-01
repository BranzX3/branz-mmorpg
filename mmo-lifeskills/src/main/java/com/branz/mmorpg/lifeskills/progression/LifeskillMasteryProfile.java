package com.branz.mmorpg.lifeskills.progression;

public record LifeskillMasteryProfile(
        int mastery, double workSpeedBonus, double basicYieldBonus, double rareYieldRelativeBonus) {
    public LifeskillMasteryProfile {
        if (mastery < 0
                || mastery > LifeskillMasteryEngine.MASTERY_MAXIMUM
                || !validBonus(workSpeedBonus, LifeskillMasteryEngine.WORK_SPEED_BONUS_CAP)
                || !validBonus(basicYieldBonus, LifeskillMasteryEngine.BASIC_YIELD_BONUS_CAP)
                || !validBonus(
                        rareYieldRelativeBonus,
                        LifeskillMasteryEngine.RARE_YIELD_RELATIVE_BONUS_CAP)) {
            throw new IllegalArgumentException("invalid lifeskill mastery profile");
        }
    }

    private static boolean validBonus(double value, double maximum) {
        return Double.isFinite(value) && value >= 0 && value <= maximum;
    }
}
