package com.branz.mmorpg.api.item;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Lossless opaque Paper item payload displaced from the reserved hotbar slot. */
public record PendingSlotItem(UUID playerId, UUID deliveryId, byte[] payload,
                              String payloadHash, Instant createdAt) {
    public PendingSlotItem {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(payloadHash, "payloadHash");
        Objects.requireNonNull(createdAt, "createdAt");
        payload = Arrays.copyOf(payload, payload.length);
        if (payload.length == 0 || payload.length > 1_048_576 || payloadHash.isBlank()) {
            throw new IllegalArgumentException("invalid pending slot item");
        }
    }

    @Override public byte[] payload() { return Arrays.copyOf(payload, payload.length); }
}
