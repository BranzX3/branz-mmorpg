package com.branz.mmorpg.api.mob;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Blocking persistence port for placed mob runtime state. */
public interface MobRepository {
    MobRuntimeSnapshot insert(MobRuntimeSnapshot mob);
    Optional<MobRuntimeSnapshot> find(UUID instanceId);
    Collection<MobRuntimeSnapshot> list();
    MobRuntimeSnapshot save(MobRuntimeSnapshot mob);
    boolean remove(UUID instanceId);
}
