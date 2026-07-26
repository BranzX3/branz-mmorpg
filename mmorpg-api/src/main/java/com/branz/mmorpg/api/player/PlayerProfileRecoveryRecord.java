package com.branz.mmorpg.api.player;

import java.time.Instant;
import java.util.Objects;

/** Durable last-resort copy written when the primary profile store cannot save. */
public record PlayerProfileRecoveryRecord(
        PlayerProfile profile, Instant recordedAt, String failureDetail) {
    public PlayerProfileRecoveryRecord {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(recordedAt, "recordedAt");
        failureDetail = Objects.requireNonNullElse(failureDetail, "");
    }
}
