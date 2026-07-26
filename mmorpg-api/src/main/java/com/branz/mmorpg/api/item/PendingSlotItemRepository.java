package com.branz.mmorpg.api.item;

import java.util.Optional;
import java.util.UUID;

/** Blocking mailbox port for a normal item displaced by the reserved UI slot. */
public interface PendingSlotItemRepository {
    Optional<PendingSlotItem> find(UUID playerId);
    PendingSlotItem store(PendingSlotItem item);
}
