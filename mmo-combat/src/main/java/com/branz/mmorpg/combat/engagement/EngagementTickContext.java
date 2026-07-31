package com.branz.mmorpg.combat.engagement;

/** Live conditions that can hold a combatant in engagement. */
public record EngagementTickContext(
        boolean hostileOwnsThreat, boolean encounterHardLock, boolean downed) {
    public static EngagementTickContext clear() {
        return new EngagementTickContext(false, false, false);
    }
}
