package com.branz.mmorpg.api.stat;

/**
 * Core attributes for MMORPG entity stat calculation.
 */
public enum AttributeType {
    MAX_HEALTH("Max Health", "HP", 100.0),
    MAX_MANA("Max Mana", "MP", 50.0),
    PHYSICAL_DAMAGE("Physical Damage", "ATK", 10.0),
    MAGIC_DAMAGE("Magic Damage", "MATK", 0.0),
    DEFENSE("Defense", "DEF", 0.0),
    MAGIC_RESIST("Magic Resistance", "MDEF", 0.0),
    CRITICAL_CHANCE("Critical Chance", "CRIT%", 0.05),
    CRITICAL_DAMAGE("Critical Damage", "CRIT DMG", 1.5),
    COOLDOWN_REDUCTION("Cooldown Reduction", "CDR%", 0.0),
    MOVEMENT_SPEED("Movement Speed", "SPD", 1.0);

    private final String displayName;
    private final String abbreviation;
    private final double defaultValue;

    AttributeType(String displayName, String abbreviation, double defaultValue) {
        this.displayName = displayName;
        this.abbreviation = abbreviation;
        this.defaultValue = defaultValue;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public double getDefaultValue() {
        return defaultValue;
    }
}
