package com.branz.mmorpg.api.combat;

import com.branz.mmorpg.api.stat.AttributeSnapshot;
import java.util.UUID;

/**
 * Everything the combat engine needs from a fighting entity — and nothing more.
 *
 * <p>A narrow port, so combat runs as pure Java tests: the Paper adapter
 * implements this over a real entity, tests implement it over a record. Combat
 * never sees a {@code Player}, an {@code Entity}, or a {@code Location}.
 */
public interface Combatant {

    UUID id();

    /** Resolved attributes at this instant. */
    AttributeSnapshot attributes();

    double currentHealth();

    /**
     * Removes health, clamped at zero.
     *
     * @return the health actually removed
     */
    double applyHealthLoss(double amount);

    /**
     * Consumes shield absorption.
     *
     * @return how much of {@code amount} was absorbed
     */
    double absorb(double amount);

    WorldPoint position();

    boolean alive();

    /** Temporarily immune to all damage, e.g. respawn protection. */
    boolean invulnerable();

    /** Standing in a zone where damage is not permitted. */
    boolean inSafeZone();

    /** True for player-controlled combatants, which is what gates PvP rules. */
    boolean playerControlled();

    /** Faction/party key. Combatants sharing one are allies. */
    String allegiance();

    default boolean allyOf(Combatant other) {
        return other != null && allegiance() != null && allegiance().equals(other.allegiance());
    }
}
