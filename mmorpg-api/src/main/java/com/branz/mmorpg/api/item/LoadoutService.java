package com.branz.mmorpg.api.item;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Optional;
import java.util.UUID;

/** Initial one-weapon loadout surface. */
public interface LoadoutService {

    Optional<WeaponDefinition> current(UUID playerId);

    EquipResult equip(UUID playerId, ContentId weaponId);

    /** Monotonic runtime revision used to invalidate buffered combat input. */
    default long revision(UUID playerId) {
        return 0L;
    }

    /** Releases per-session runtime state after logout. */
    default void forget(UUID playerId) {
    }

    record EquipResult(boolean equipped, String rejection, WeaponDefinition weapon) {
    }
}
