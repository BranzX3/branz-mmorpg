package com.branz.mmorpg.core.combat;

import com.branz.mmorpg.api.combat.CombatPolicy;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks who is in combat.
 *
 * <p>Combat state gates regeneration, equipment swapping, and logout handling, so
 * it must be driven by damage actually landing rather than by a client telling
 * the server it is fighting.
 *
 * <p>Timestamps rather than timers: a combatant leaves combat because time has
 * passed since their last eligible action, which one sweep can evaluate for
 * everyone at once.
 */
public final class CombatStateTracker {

    private final Map<UUID, Instant> lastCombatAt = new HashMap<>();
    private final CombatPolicy policy;

    public CombatStateTracker(CombatPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * Records combat activity.
     *
     * @return true when this put the combatant into combat, i.e. they were out of it
     */
    public boolean touch(UUID combatantId, Instant now) {
        Objects.requireNonNull(combatantId, "combatantId");
        Objects.requireNonNull(now, "now");
        boolean entering = !inCombat(combatantId, now);
        lastCombatAt.put(combatantId, now);
        return entering;
    }

    public boolean inCombat(UUID combatantId, Instant now) {
        Instant last = lastCombatAt.get(combatantId);
        if (last == null) {
            return false;
        }
        return now.toEpochMilli() - last.toEpochMilli() < policy.combatTimeoutMillis();
    }

    /**
     * Drops combatants whose inactivity expired.
     *
     * @return those that just left combat
     */
    public List<UUID> sweep(Instant now) {
        Objects.requireNonNull(now, "now");
        List<UUID> left = new java.util.ArrayList<>();
        lastCombatAt.entrySet().removeIf(entry -> {
            boolean expired = now.toEpochMilli() - entry.getValue().toEpochMilli()
                    >= policy.combatTimeoutMillis();
            if (expired) {
                left.add(entry.getKey());
            }
            return expired;
        });
        return List.copyOf(left);
    }

    /** Clears state for a combatant. Called on death and on logout. */
    public void clear(UUID combatantId) {
        lastCombatAt.remove(combatantId);
    }

    public int tracked() {
        return lastCombatAt.size();
    }
}
