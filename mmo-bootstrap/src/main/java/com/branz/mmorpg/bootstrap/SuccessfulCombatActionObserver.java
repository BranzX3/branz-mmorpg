package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.UUID;

@FunctionalInterface
interface SuccessfulCombatActionObserver {
    SuccessfulCombatActionObserver NONE = (actorId, actionId, moveId, currentTick) -> {};

    void observe(CharacterId actorId, UUID actionId, DefinitionId moveId, long currentTick);
}
