package com.branz.mmorpg.scenes;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical navigation contract for the V1 local Adventurer's Chronicle.
 *
 * <p>Primary root categories are intentionally separate from contextual Rest workflows and
 * compatibility overlays so presentation adapters cannot accidentally promote convenience pages
 * into the authored V1 root information architecture.
 */
public final class SceneHubNavigationPolicy {
    private static final List<SceneMode> PRIMARY_ROOT_MODES =
            List.of(
                    SceneMode.EQUIPMENT,
                    SceneMode.WARDROBE_DYE,
                    SceneMode.COMBAT_ARTS,
                    SceneMode.FORMS,
                    SceneMode.MAGIC_ATTUNEMENT,
                    SceneMode.CHARACTER_INFORMATION);

    private static final Set<SceneMode> REST_CONTEXTUAL_MODES =
            Set.of(SceneMode.FLASK_PREPARATION);

    private static final Set<SceneMode> COMPATIBILITY_OVERLAY_MODES =
            Set.of(SceneMode.JOURNAL_PENDING_REWARDS, SceneMode.SETTINGS_HELP);

    private SceneHubNavigationPolicy() {}

    public static List<SceneMode> primaryRootModes() {
        return PRIMARY_ROOT_MODES;
    }

    public static Set<SceneMode> restContextualModes() {
        return REST_CONTEXTUAL_MODES;
    }

    public static Set<SceneMode> compatibilityOverlayModes() {
        return COMPATIBILITY_OVERLAY_MODES;
    }

    public static boolean isPrimaryRootMode(SceneMode mode) {
        return PRIMARY_ROOT_MODES.contains(Objects.requireNonNull(mode, "mode"));
    }

    public static boolean isRestContextualMode(SceneMode mode) {
        return REST_CONTEXTUAL_MODES.contains(Objects.requireNonNull(mode, "mode"));
    }

    public static boolean isCompatibilityOverlayMode(SceneMode mode) {
        return COMPATIBILITY_OVERLAY_MODES.contains(Objects.requireNonNull(mode, "mode"));
    }

    static void requireLocalCharacterHubModes(Set<SceneMode> availableModes) {
        Objects.requireNonNull(availableModes, "availableModes");
        EnumSet<SceneMode> required = EnumSet.noneOf(SceneMode.class);
        required.add(SceneMode.HUB);
        required.addAll(PRIMARY_ROOT_MODES);
        required.addAll(REST_CONTEXTUAL_MODES);
        if (availableModes.containsAll(required)) {
            return;
        }
        required.removeAll(availableModes);
        throw new IllegalArgumentException(
                "local character hub is missing required navigation modes: " + required);
    }
}
