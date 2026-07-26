package com.branz.mmorpg.core.combat;

import com.branz.mmorpg.api.combat.DamageResult;
import com.branz.mmorpg.api.combat.DamageType;
import com.branz.mmorpg.api.combat.DeathContext;
import com.branz.mmorpg.api.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Events published by the combat engine after damage has been applied. */
public final class CombatEvents {

    private CombatEvents() {
    }

    /**
     * Damage that landed. Published only after the health mutation succeeded, so
     * a consumer never sees damage that did not happen.
     */
    public record DamageDealt(
            UUID eventId,
            Instant occurredAt,
            UUID castId,
            UUID attackerId,
            UUID targetId,
            DamageType type,
            DamageResult result) implements DomainEvent {
    }

    /**
     * A combatant died.
     *
     * <p>The context includes final-blow attribution plus the immutable
     * contribution ledger used by rewards, mastery, and encounter consumers.
     */
    public record CombatantDied(
            UUID eventId,
            Instant occurredAt,
            DeathContext context) implements DomainEvent {

        public CombatantDied {
            java.util.Objects.requireNonNull(context, "context");
        }

        public UUID victimId() { return context.victimId(); }
        public UUID killerId() { return context.killerId(); }
        public DamageType cause() { return context.cause(); }
        public double overkill() { return context.overkill(); }
    }

    /** A combatant entered or left combat state. */
    public record CombatStateChanged(
            UUID eventId,
            Instant occurredAt,
            UUID combatantId,
            boolean inCombat) implements DomainEvent {
    }
}
