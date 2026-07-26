package com.branz.mmorpg.api.gathering;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface GatheringService {
    Optional<GatheringNodeInstance> findAt(WorldBlockPosition position);

    Collection<GatheringNodeInstance> nodes();

    GatheringNodeInstance place(
            ContentId definitionId, WorldBlockPosition position, UUID createdBy);

    boolean remove(UUID nodeInstanceId);

    GatheringReservation begin(
            UUID playerId, WorldBlockPosition position, Set<String> heldToolTags,
            boolean regionAllowed, boolean presentationMatches);

    GatheringResult complete(GatheringReservation reservation);

    void interrupt(GatheringReservation reservation, String reason);

    GatheringNodeInstance setState(
            UUID nodeInstanceId, GatheringNodeState state, Instant now);
}
