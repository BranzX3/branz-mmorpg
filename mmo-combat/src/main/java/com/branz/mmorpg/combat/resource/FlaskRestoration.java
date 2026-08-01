package com.branz.mmorpg.combat.resource;

public record FlaskRestoration(
        double maximumHealthRatio, double maximumManaRatio, int stamina, boolean clearsExhausted) {
    public FlaskRestoration {
        if (!validRatio(maximumHealthRatio) || !validRatio(maximumManaRatio) || stamina < 0) {
            throw new IllegalArgumentException("invalid Flask restoration");
        }
    }

    public static FlaskRestoration forDose(FlaskDose dose) {
        return switch (dose) {
            case HEALING -> new FlaskRestoration(0.35, 0, 0, false);
            case MANA -> new FlaskRestoration(0, 0.40, 0, false);
            case STAMINA -> new FlaskRestoration(0, 0, 60, true);
        };
    }

    private static boolean validRatio(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }
}
