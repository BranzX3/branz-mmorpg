package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.social.pvp.PvpCombatProfile;
import java.util.Optional;
import org.bukkit.entity.Player;

interface PvpCombatPolicy {
    PvpCombatPolicy NONE =
            new PvpCombatPolicy() {
                @Override
                public Optional<PvpCombatProfile> profile(Player attacker, Player defender) {
                    return Optional.empty();
                }

                @Override
                public Optional<PvpCombatProfile> activeProfile(Player player) {
                    return Optional.empty();
                }
            };

    Optional<PvpCombatProfile> profile(Player attacker, Player defender);

    Optional<PvpCombatProfile> activeProfile(Player player);
}
