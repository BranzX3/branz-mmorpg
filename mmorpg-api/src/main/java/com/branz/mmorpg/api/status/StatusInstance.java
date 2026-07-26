package com.branz.mmorpg.api.status;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.stat.ModifierSource;
import java.time.Instant;
import java.time.Duration;
import java.util.Objects;

/**
 * One active status on one target.
 *
 * <p>{@code source} is retained for the whole lifetime because kill credit,
 * contribution, and threat all need to know who applied the bleed that finished
 * a target — attribution cannot be reconstructed after the fact.
 *
 * @param instanceId  identity, so independent stacks stay distinguishable
 * @param definitionId status definition
 * @param source      who applied it
 * @param stacks      current stack count, at least 1
 * @param appliedAt   when it was first applied
 * @param expiresAt   when it lapses; null means permanent
 * @param nextTickAt  when the next periodic tick is due; null when not periodic
 */
public record StatusInstance(
        long instanceId,
        ContentId definitionId,
        ModifierSource source,
        int stacks,
        Instant appliedAt,
        Instant expiresAt,
        Instant nextTickAt) {

    public StatusInstance {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(appliedAt, "appliedAt");
        if (stacks < 1) {
            throw new IllegalArgumentException("stacks must be at least 1: " + stacks);
        }
    }

    public boolean permanent() {
        return expiresAt == null;
    }

    public boolean expiredAt(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public boolean tickDueAt(Instant now) {
        return nextTickAt != null && !now.isBefore(nextTickAt);
    }

    /** Remaining duration in milliseconds; {@link Long#MAX_VALUE} when permanent. */
    public long remainingMillis(Instant now) {
        if (expiresAt == null) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, expiresAt.toEpochMilli() - now.toEpochMilli());
    }

    /** The modifier source key used for the attribute modifiers this instance grants. */
    public String modifierPrefix() {
        return "status:" + definitionId + ":" + instanceId;
    }

    public StatusInstance withStacks(int newStacks) {
        return new StatusInstance(instanceId, definitionId, source, newStacks,
                appliedAt, expiresAt, nextTickAt);
    }

    public StatusInstance withExpiry(Instant newExpiry) {
        return new StatusInstance(instanceId, definitionId, source, stacks,
                appliedAt, newExpiry, nextTickAt);
    }

    public StatusInstance withNextTick(Instant next) {
        return new StatusInstance(instanceId, definitionId, source, stacks,
                appliedAt, expiresAt, next);
    }

    /** Moves expiry and periodic schedule forward while an offline PAUSE was active. */
    public StatusInstance shiftedBy(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        return new StatusInstance(instanceId, definitionId, source, stacks, appliedAt,
                expiresAt == null ? null : expiresAt.plus(duration),
                nextTickAt == null ? null : nextTickAt.plus(duration));
    }
}
