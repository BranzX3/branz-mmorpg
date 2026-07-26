package com.branz.mmorpg.api.item;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Optional;
import java.util.UUID;

/** Initial one-weapon loadout surface. */
public interface LoadoutService {

    Optional<WeaponDefinition> current(UUID playerId);

    EquipResult equip(UUID playerId, ContentId weaponId);

    record EquipResult(boolean equipped, String rejection, WeaponDefinition weapon) {
    }
}
