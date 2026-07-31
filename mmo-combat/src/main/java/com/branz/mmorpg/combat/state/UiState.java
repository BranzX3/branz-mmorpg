package com.branz.mmorpg.combat.state;

public enum UiState {
    NONE,
    VANILLA_INVENTORY,
    SCENE,
    DIALOGUE,
    CUTSCENE,
    MARKET,
    CRAFTING;

    public boolean closesOnDanger() {
        return this == SCENE || this == DIALOGUE || this == MARKET || this == CRAFTING;
    }

    public boolean allowedWhileEngaged() {
        return this == NONE || this == VANILLA_INVENTORY || this == CUTSCENE;
    }
}
