package com.branz.mmorpg.api.event;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable fact published after the authoritative transaction that produced
 * it has succeeded.
 *
 * <p>Delivery is at-least-once, so every event carries a stable
 * {@link #eventId()} and consumers deduplicate on it. Core must never publish an
 * event for a cancelled, rolled-back, synthetic, or administratively previewed
 * action.
 */
public interface DomainEvent {

    /** Stable identity, for consumer-side deduplication. */
    UUID eventId();

    Instant occurredAt();
}
