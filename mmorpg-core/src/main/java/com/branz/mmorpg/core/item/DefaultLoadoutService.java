package com.branz.mmorpg.core.item;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.item.LoadoutService;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.core.player.PlayerSessionService;
import com.branz.mmorpg.api.runtime.GameClock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Validates and persists the initial one-weapon loadout.
 *
 * <p>It does not touch skill/status runtime state, so moving an item cannot
 * reset cooldowns or cleanse statuses.
 */
public final class DefaultLoadoutService implements LoadoutService {

    private final PlayerSessionService sessions;
    private final Supplier<ContentSnapshot> content;
    private final BiPredicate<UUID, java.time.Instant> inCombat;
    private final GameClock clock;

    public DefaultLoadoutService(PlayerSessionService sessions,
                                 Supplier<ContentSnapshot> content,
                                 BiPredicate<UUID, java.time.Instant> inCombat,
                                 GameClock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.content = Objects.requireNonNull(content, "content");
        this.inCombat = Objects.requireNonNull(inCombat, "inCombat");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<WeaponDefinition> current(UUID playerId) {
        var profile = sessions.requirePlayable(playerId).profile();
        return profile.selectedLoadoutId().flatMap(id -> Optional.ofNullable(
                content.get().weapons().get(id)));
    }

    @Override
    public EquipResult equip(UUID playerId, ContentId weaponId) {
        var session = sessions.requirePlayable(playerId);
        if (inCombat.test(playerId, clock.now())) {
            return new EquipResult(false, "loadout changes are blocked in combat", null);
        }
        WeaponDefinition weapon = content.get().weapons().get(weaponId);
        if (weapon == null) {
            return new EquipResult(false, "unknown weapon " + weaponId, null);
        }
        session.updateProfile(profile -> profile.withSelectedLoadout(weaponId));
        return new EquipResult(true, null, weapon);
    }
}
