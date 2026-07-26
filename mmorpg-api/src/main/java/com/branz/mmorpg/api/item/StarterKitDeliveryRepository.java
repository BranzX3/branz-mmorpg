package com.branz.mmorpg.api.item;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Blocking durable outbox for starter inventory delivery. */
public interface StarterKitDeliveryRepository {
    Optional<StarterKitDelivery> find(UUID playerId);
    boolean markDelivered(UUID playerId, Instant deliveredAt);
}
