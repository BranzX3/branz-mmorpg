package com.branz.mmorpg.core.gathering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.gathering.GatheringNodeInstance;
import com.branz.mmorpg.api.gathering.GatheringNodeState;
import com.branz.mmorpg.api.gathering.WorldBlockPosition;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GatheringNodeEngineTest {
    private static final UUID NODE =
            UUID.fromString("6a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f3");
    private static final UUID WORLD =
            UUID.fromString("5a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f4");
    private static final UUID PLAYER =
            UUID.fromString("9a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");
    private static final UUID OTHER =
            UUID.fromString("4a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f5");
    private static final Instant NOW = Instant.parse("2026-07-26T00:00:00Z");
    private final GatheringNodeEngine engine = new GatheringNodeEngine();

    @Test
    void exactlyOnePlayerOwnsAReservation() {
        GatheringNodeInstance placed = placed();
        GatheringNodeInstance reserved = engine.reserve(
                placed, PLAYER, NOW, Duration.ofSeconds(3), Duration.ofSeconds(1));

        assertEquals(GatheringNodeState.RESERVED, reserved.state());
        assertEquals(PLAYER, reserved.reservedBy().orElseThrow());
        assertThrows(IllegalStateException.class, () -> engine.reserve(
                reserved, OTHER, NOW, Duration.ofSeconds(3), Duration.ofSeconds(1)));
    }

    @Test
    void expiredReservationReleasesAndDepletedNodeRespawnsByTimestamp() {
        GatheringNodeInstance reserved = engine.reserve(
                placed(), PLAYER, NOW, Duration.ofSeconds(3), Duration.ofSeconds(1));
        GatheringNodeInstance reclaimed = engine.reserve(
                reserved, OTHER, NOW.plusSeconds(4), Duration.ofSeconds(3), Duration.ZERO);
        GatheringNodeInstance depleted = engine.deplete(
                reclaimed, OTHER, reclaimed.reservationSequence(),
                NOW.plusMillis(4500), NOW.plusSeconds(65));

        assertEquals(GatheringNodeState.DEPLETED,
                engine.normalize(depleted, NOW.plusSeconds(64)).state());
        assertEquals(GatheringNodeState.AVAILABLE,
                engine.normalize(depleted, NOW.plusSeconds(65)).state());
    }

    @Test
    void staleCompletionCannotHarvestANewerReservation() {
        GatheringNodeInstance first = engine.reserve(
                placed(), PLAYER, NOW, Duration.ofSeconds(1), Duration.ZERO);
        GatheringNodeInstance second = engine.reserve(
                first, PLAYER, NOW.plusSeconds(1), Duration.ofSeconds(1), Duration.ZERO);

        assertThrows(IllegalStateException.class, () -> engine.deplete(
                second, PLAYER, first.reservationSequence(),
                NOW.plusMillis(1500), NOW.plusSeconds(30)));
    }

    private static GatheringNodeInstance placed() {
        return GatheringNodeInstance.placed(NODE, ContentId.parse("branz:aether_deposit"),
                new WorldBlockPosition(WORLD, 10, 64, 20), PLAYER, NOW);
    }
}
