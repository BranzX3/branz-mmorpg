package com.branz.mmorpg.combat.input;

import com.branz.mmorpg.combat.state.ActionState;
import com.branz.mmorpg.combat.state.EngagementState;
import com.branz.mmorpg.combat.state.UiState;
import com.branz.mmorpg.combat.state.WeaponState;
import java.util.Objects;

public record InputPolicyContext(
        EngagementState engagement,
        WeaponState weapon,
        ActionState action,
        UiState ui,
        boolean hardWorldTarget,
        DirectionSnapshot direction) {
    public InputPolicyContext {
        Objects.requireNonNull(engagement, "engagement");
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(ui, "ui");
        Objects.requireNonNull(direction, "direction");
    }
}
