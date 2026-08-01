package com.branz.mmorpg.lifeskills.progression;

public enum LifeskillRankTier {
    TRAINEE("Trainee"),
    SKILLED("Skilled"),
    PROFESSIONAL("Professional"),
    ARTISAN("Artisan"),
    MASTER("Master"),
    GRANDMASTER("Grandmaster");

    private final String displayName;

    LifeskillRankTier(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
