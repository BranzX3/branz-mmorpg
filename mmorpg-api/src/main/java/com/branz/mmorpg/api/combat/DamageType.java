package com.branz.mmorpg.api.combat;

import com.branz.mmorpg.api.stat.AttributeType;

/** How damage is mitigated. */
public enum DamageType {

    /** Reduced by Defense. */
    PHYSICAL(AttributeType.DEFENSE),

    /** Reduced by Magic Resistance. */
    MAGIC(AttributeType.MAGIC_RESISTANCE),

    /** Ignores mitigation entirely. Reserved for content that must not be tanked. */
    TRUE(null),

    /** Fall damage, drowning, void. Not attributable to a combatant. */
    ENVIRONMENTAL(null);

    private final AttributeType mitigatedBy;

    DamageType(AttributeType mitigatedBy) {
        this.mitigatedBy = mitigatedBy;
    }

    /** The attribute that reduces this type, or null when nothing does. */
    public AttributeType mitigatedBy() {
        return mitigatedBy;
    }

    public boolean mitigable() {
        return mitigatedBy != null;
    }

    /** Whether this type can crit. Environmental and true damage cannot. */
    public boolean critical() {
        return this == PHYSICAL || this == MAGIC;
    }
}
