package com.branz.mmorpg.combat.state;

import java.util.Objects;

public record CombatStateSnapshot(
        EngagementState engagement,
        ActionState action,
        UiState ui,
        EncounterState encounter,
        long revision) {
    public CombatStateSnapshot {
        Objects.requireNonNull(engagement, "engagement");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(ui, "ui");
        Objects.requireNonNull(encounter, "encounter");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        if (engagement == EngagementState.ENGAGED && !ui.allowedWhileEngaged()) {
            throw new IllegalArgumentException("exclusive UI cannot remain open while engaged");
        }
    }

    public static CombatStateSnapshot initial() {
        return new CombatStateSnapshot(
                EngagementState.EXPLORATION,
                ActionState.IDLE,
                UiState.NONE,
                EncounterState.DORMANT,
                0);
    }
}
