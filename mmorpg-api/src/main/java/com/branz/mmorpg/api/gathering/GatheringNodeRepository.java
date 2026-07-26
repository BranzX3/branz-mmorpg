package com.branz.mmorpg.api.gathering;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.lifeskill.LifeSkillSnapshot;
import com.branz.mmorpg.api.operation.OperationId;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Blocking world-state persistence port. */
public interface GatheringNodeRepository {
    GatheringNodeInstance place(GatheringNodeInstance node);

    Optional<GatheringNodeInstance> findAt(WorldBlockPosition position);

    Optional<GatheringNodeInstance> find(UUID instanceId);

    Collection<GatheringNodeInstance> list();

    boolean remove(UUID instanceId);

    GatheringNodeInstance reserve(UUID instanceId, UUID playerId, Instant now,
                                  Duration harvestTime, Duration grace);

    GatheringNodeInstance release(UUID instanceId, UUID playerId,
                                  long reservationSequence, Instant now);

    GatheringHarvestCommit commitHarvest(
            UUID instanceId, UUID playerId, long reservationSequence,
            ContentId skillId, OperationId operationId, Instant now, Instant respawnAt,
            UnaryOperator<LifeSkillSnapshot> skillMutation,
            UnaryOperator<InventorySnapshot> inventoryMutation);

    GatheringNodeInstance setState(UUID instanceId, GatheringNodeState state, Instant now);
}
