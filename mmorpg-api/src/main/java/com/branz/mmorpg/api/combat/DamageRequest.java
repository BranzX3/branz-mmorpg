package com.branz.mmorpg.api.combat;

import java.util.Objects;
import java.util.UUID;

/**
 * An intent to deal damage. Server-authored only.
 *
 * <p>Nothing here comes from a client. {@code basePower} is derived from the
 * server's own view of the attacker's stats and the skill definition, because a
 * client-reported number is a client-chosen number.
 *
 * @param castId       identifies the cast; damage from one cast to one target is
 *                     deduplicated on this
 * @param attackerId   attacker, null for environmental damage
 * @param targetId     target
 * @param type         damage type
 * @param basePower    server-computed power before any modifier
 * @param range        maximum permitted distance; non-positive means unchecked
 * @param requiresLineOfSight whether line of sight is required
 * @param maxHitsPerTarget how often this cast may hit one target, at least 1
 */
public record DamageRequest(
        UUID castId,
        UUID attackerId,
        UUID targetId,
        DamageType type,
        double basePower,
        double range,
        boolean requiresLineOfSight,
        int maxHitsPerTarget) {

    /** Hard safety bound; balance content is expected to be far below this. */
    public static final double MAX_BASE_POWER = 1_000_000_000.0;

    public DamageRequest {
        Objects.requireNonNull(castId, "castId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(type, "type");
        if (maxHitsPerTarget < 1) {
            throw new IllegalArgumentException("maxHitsPerTarget must be at least 1");
        }
    }

    public static DamageRequest melee(UUID castId, UUID attackerId, UUID targetId,
                                      DamageType type, double basePower, double range) {
        return new DamageRequest(castId, attackerId, targetId, type, basePower, range, true, 1);
    }

    public static DamageRequest environmental(UUID castId, UUID targetId, double basePower) {
        return new DamageRequest(castId, null, targetId, DamageType.ENVIRONMENTAL, basePower,
                0.0, false, 1);
    }

    public boolean environmental() {
        return type == DamageType.ENVIRONMENTAL;
    }

    public boolean validPower() {
        return Double.isFinite(basePower) && basePower > 0.0 && basePower <= MAX_BASE_POWER;
    }

    public boolean validRange() {
        return Double.isFinite(range) && range >= 0.0;
    }
}
