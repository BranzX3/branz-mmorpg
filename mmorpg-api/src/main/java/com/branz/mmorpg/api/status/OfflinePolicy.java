package com.branz.mmorpg.api.status;

/**
 * What happens to a status while its holder is offline.
 *
 * <p>Explicit per definition, because leaving it implicit produces exploits in
 * both directions: a debuff that pauses lets a player log out to dodge it, and a
 * buff that ticks down lets a player lose a purchase to a disconnect.
 */
public enum OfflinePolicy {

    /** Duration keeps running. A debuff cannot be dodged by logging out. */
    TICK_DOWN,

    /** Duration freezes and resumes on login. Fair for paid or earned buffs. */
    PAUSE,

    /** The status is dropped at logout. */
    CLEAR
}
