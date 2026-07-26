package com.branz.mmorpg.quest.api;

import java.util.UUID;

public interface AccessibilitySettingsStore {
    AccessibilitySettings load(UUID playerId);
    AccessibilitySettings save(AccessibilitySettings settings);
}
