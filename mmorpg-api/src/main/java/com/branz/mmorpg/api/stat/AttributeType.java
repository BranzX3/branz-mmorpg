package com.branz.mmorpg.api.stat;

/**
 * The attributes the combat engine resolves.
 *
 * <p>Every attribute declares its own bounds, and every percentage attribute has
 * a documented cap as required by CORE_MMO_SPECIFICATION §C2. The cap lives here
 * rather than in the formula that reads the value, so a new consumer cannot
 * forget to apply it: resolution clamps once, centrally.
 *
 * <p>Ratio attributes are expressed as fractions ({@code 0.60} = 60%).
 * Multiplier attributes are expressed relative to 1.0, so a 30% movement bonus
 * is a value of 1.30.
 */
public enum AttributeType {

    MAX_HEALTH("Maximum Health", "HP", 100.0, 1.0, 1_000_000.0),
    MAX_MANA("Maximum Mana", "MP", 50.0, 0.0, 1_000_000.0),
    MAX_STAMINA("Maximum Stamina", "SP", 100.0, 0.0, 1_000_000.0),

    PHYSICAL_POWER("Physical Power", "ATK", 10.0, 0.0, 1_000_000.0),
    MAGIC_POWER("Magic Power", "MATK", 0.0, 0.0, 1_000_000.0),
    DEFENSE("Defense", "DEF", 0.0, 0.0, 1_000_000.0),
    MAGIC_RESISTANCE("Magic Resistance", "MDEF", 0.0, 0.0, 1_000_000.0),
    HEALING_POWER("Healing Power", "HEAL", 0.0, 0.0, 1_000_000.0),

    /** Fraction. Capped at 60%. */
    CRITICAL_CHANCE("Critical Chance", "CRIT", 0.05, 0.0, 0.60),
    /** Damage multiplier on a critical hit. */
    CRITICAL_DAMAGE("Critical Damage", "CRIT DMG", 1.5, 1.0, 5.0),
    /** Fraction of cooldown removed. Capped at 35%. */
    COOLDOWN_RECOVERY("Cooldown Recovery", "CDR", 0.0, 0.0, 0.35),
    /** Fraction of crowd-control duration resisted. Capped at 60%. */
    CROWD_CONTROL_RESISTANCE("Crowd-Control Resistance", "CCR", 0.0, 0.0, 0.60),

    /** Multiplier relative to 1.0. Capped at +30%. */
    MOVEMENT_SPEED("Movement Speed", "SPD", 1.0, 0.30, 1.30),
    /** Multiplier relative to 1.0. */
    ATTACK_SPEED("Attack Speed", "ASPD", 1.0, 0.20, 3.0);

    private final String displayName;
    private final String abbreviation;
    private final double defaultValue;
    private final double minimum;
    private final double maximum;

    AttributeType(String displayName, String abbreviation,
                  double defaultValue, double minimum, double maximum) {
        this.displayName = displayName;
        this.abbreviation = abbreviation;
        this.defaultValue = defaultValue;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public String displayName() {
        return displayName;
    }

    public String abbreviation() {
        return abbreviation;
    }

    public double defaultValue() {
        return defaultValue;
    }

    public double minimum() {
        return minimum;
    }

    /** Documented cap. Resolution never produces a value above this. */
    public double maximum() {
        return maximum;
    }

    /** Whether this attribute is a resource pool maximum. */
    public boolean resourceMaximum() {
        return this == MAX_HEALTH || this == MAX_MANA || this == MAX_STAMINA;
    }

    /** Clamps {@code value} into this attribute's documented range. */
    public double clamp(double value) {
        return Math.min(maximum, Math.max(minimum, value));
    }
}
