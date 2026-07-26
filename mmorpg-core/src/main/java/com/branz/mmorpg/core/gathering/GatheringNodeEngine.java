package com.branz.mmorpg.core.gathering;

import com.branz.mmorpg.api.gathering.GatheringNodeInstance;
import com.branz.mmorpg.api.gathering.GatheringNodeState;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Pure authoritative gathering-node lifecycle. */
public final class GatheringNodeEngine {

    public GatheringNodeInstance normalize(GatheringNodeInstance node, Instant now) {
        if (node.state() == GatheringNodeState.RESERVED
                && !node.reservedUntil().orElseThrow().isAfter(now)) {
            return available(node);
        }
        if (node.state() == GatheringNodeState.DEPLETED
                && !node.respawnAt().orElseThrow().isAfter(now)) {
            return available(node);
        }
        return node;
    }

    public GatheringNodeInstance reserve(
            GatheringNodeInstance original, UUID playerId, Instant now,
            Duration harvestTime, Duration grace) {
        GatheringNodeInstance node = normalize(original, now);
        if (node.state() == GatheringNodeState.BROKEN) {
            throw new IllegalStateException("NODE_BROKEN");
        }
        if (node.state() == GatheringNodeState.DEPLETED) {
            throw new IllegalStateException("NODE_DEPLETED");
        }
        if (node.state() == GatheringNodeState.RESERVED) {
            throw new IllegalStateException("NODE_TAKEN");
        }
        long sequence = Math.addExact(node.reservationSequence(), 1);
        return new GatheringNodeInstance(node.instanceId(), node.definitionId(), node.position(),
                GatheringNodeState.RESERVED, sequence, Optional.empty(),
                Optional.of(playerId), Optional.of(now.plus(harvestTime).plus(grace)),
                node.lastHarvestedBy(), node.lastHarvestedAt(), node.createdBy(), node.createdAt());
    }

    public GatheringNodeInstance release(
            GatheringNodeInstance original, UUID playerId, long sequence, Instant now) {
        GatheringNodeInstance node = normalize(original, now);
        if (node.state() != GatheringNodeState.RESERVED) return node;
        requireReservation(node, playerId, sequence, now);
        return available(node);
    }

    public GatheringNodeInstance deplete(
            GatheringNodeInstance node, UUID playerId, long sequence,
            Instant now, Instant respawnAt) {
        requireReservation(node, playerId, sequence, now);
        if (!respawnAt.isAfter(now)) throw new IllegalArgumentException("respawn must be future");
        return new GatheringNodeInstance(node.instanceId(), node.definitionId(), node.position(),
                GatheringNodeState.DEPLETED, node.reservationSequence(),
                Optional.of(respawnAt), Optional.empty(), Optional.empty(),
                Optional.of(playerId), Optional.of(now), node.createdBy(), node.createdAt());
    }

    public GatheringNodeInstance broken(GatheringNodeInstance node) {
        return new GatheringNodeInstance(node.instanceId(), node.definitionId(), node.position(),
                GatheringNodeState.BROKEN, node.reservationSequence(), Optional.empty(),
                Optional.empty(), Optional.empty(), node.lastHarvestedBy(),
                node.lastHarvestedAt(), node.createdBy(), node.createdAt());
    }

    private static void requireReservation(
            GatheringNodeInstance node, UUID playerId, long sequence, Instant now) {
        if (node.state() != GatheringNodeState.RESERVED
                || !node.reservedBy().orElseThrow().equals(playerId)
                || node.reservationSequence() != sequence
                || !node.reservedUntil().orElseThrow().isAfter(now)) {
            throw new IllegalStateException("INTERRUPTED");
        }
    }

    private static GatheringNodeInstance available(GatheringNodeInstance node) {
        return new GatheringNodeInstance(node.instanceId(), node.definitionId(), node.position(),
                GatheringNodeState.AVAILABLE, node.reservationSequence(), Optional.empty(),
                Optional.empty(), Optional.empty(), node.lastHarvestedBy(),
                node.lastHarvestedAt(), node.createdBy(), node.createdAt());
    }
}
