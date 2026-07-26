package com.branz.mmorpg.quest.api;

import java.util.UUID;

public record AccessibilitySettings(
        UUID playerId,
        DialogueMode dialogueMode,
        double textSpeed,
        boolean skipPreviouslyRead,
        Intensity portraitIntensity,
        Intensity vfxIntensity,
        boolean soundAlternatives) {
    public enum DialogueMode { MANUAL, AUTO, FAST }
    public enum Intensity { OFF, LOW, FULL }
    public AccessibilitySettings {
        if (!Double.isFinite(textSpeed) || textSpeed < 0.25 || textSpeed > 4) {
            throw new IllegalArgumentException("text speed must be in [0.25,4]");
        }
    }
    public static AccessibilitySettings defaults(UUID playerId) {
        return new AccessibilitySettings(playerId, DialogueMode.MANUAL, 1,
                true, Intensity.FULL, Intensity.FULL, true);
    }
}
