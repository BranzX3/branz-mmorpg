package com.branz.mmorpg.api.status;

/** Whether a status helps, harms, or neither. Drives cleanse rules and UI colour. */
public enum StatusCategory {

    /** A buff. Removed by a dispel, not by a cleanse. */
    POSITIVE,

    /** A debuff. Removed by a cleanse. */
    NEGATIVE,

    /** Neither, e.g. a marker or a quest state. Removed only explicitly. */
    NEUTRAL;

    public boolean harmful() {
        return this == NEGATIVE;
    }
}
